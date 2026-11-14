package com.shaterguy.chatgptpromptscheduler;

import java.util.regex.Pattern;

/** Reads only failed ChatGPT API responses; it leaves the page's response intact. */
final class HttpFailureObserver {
    static final String BRIDGE = "SchedulerHttpDiagnostics";
    static final int CHUNK_CHARS = 8192;
    private static final Pattern SECRET_FIELD = Pattern.compile(
            "(?i)(\"[^\"]*(?:token|authorization|cookie|secret|password|api[_-]?key|credential|e[_-]?mail|session[_-]?id|prompt|input[_-]?text|refresh[_-]?token|session[_-]?key)[^\"]*\"\\s*:\\s*)\"(?:\\\\.|[^\"\\\\])*+(?:\"|$)");
    private static final Pattern BEARER = Pattern.compile("(?i)\\bBearer\\s+[A-Za-z0-9._~+/=-]+");
    private static final Pattern EMAIL = Pattern.compile("(?i)\\b[A-Z0-9._%+\\-]+@[A-Z0-9.\\-]+\\.[A-Z]{2,}\\b");
    private static final Pattern URL_SECRET = Pattern.compile("(?i)([?&](?:[^=&#\\s]*(?:token|key|secret|password|auth|code)[^=&#\\s]*)=)[^&#\\s\"<>]*");
    private static final Pattern SENSITIVE_HEADER = Pattern.compile(
            "(?im)^([ \\t]*(?:set-cookie|cookie|authorization|proxy-authorization|x-api-key)\\s*:)[^\\r\\n]*");
    private static final Pattern JWT = Pattern.compile("\\beyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]*");

    private HttpFailureObserver() {}

    static String sanitizeBody(String body) {
        String result = SECRET_FIELD.matcher(body).replaceAll("$1\"[redacted]\"");
        result = BEARER.matcher(result).replaceAll("Bearer [redacted]");
        result = EMAIL.matcher(result).replaceAll("[redacted email]");
        result = URL_SECRET.matcher(result).replaceAll("$1[redacted]");
        result = SENSITIVE_HEADER.matcher(result).replaceAll("$1 [redacted]");
        return JWT.matcher(result).replaceAll("[redacted token]");
    }

    static String script() {
        return """
                (() => {
                    if (window.top !== window || !window.SchedulerHttpDiagnostics) return;
                    const post = window.SchedulerHttpDiagnostics.postMessage.bind(window.SchedulerHttpDiagnostics);
                    const chunkSize = 8192;
                    let sequence = 0;
                    const prefix = Math.random().toString(36).slice(2) + '-';
                    const origins = new Set(['https://chatgpt.com', 'https://www.chatgpt.com']);
                    const pathOf = value => {
                        try {
                            const url = new URL(value, location.href);
                            if (!origins.has(url.origin) || !/^\\/(?:backend-api(?:-anon)?|api)(?:\\/|$)/.test(url.pathname)) return '';
                            return url.pathname.slice(0, 256);
                        } catch (_) { return ''; }
                    };
                    const report = (path, method, code, requestId, bodyState, body = '') => {
                        try { post(JSON.stringify({path, method, code, requestId, bodyState, body})); } catch (_) {}
                    };
                    const splitResponse = (path, method, code, requestId) => {
                        const id = prefix + (++sequence);
                        const emit = value => post(JSON.stringify({...value, id}));
                        emit({part:'start', path, method, code, requestId});
                        let hasText = false;
                        return {
                            write(text) {
                                for (let i = 0; i < text.length; i += chunkSize) {
                                    emit({part:'chunk', body:text.slice(i, i + chunkSize)});
                                    hasText = true;
                                }
                            },
                            end(state) { emit({part:'end', bodyState:state || (hasText ? 'captured' : 'empty')}); }
                        };
                    };
                    const reportText = (path, method, code, id, body) => {
                        const capture = splitResponse(path, method, code, id);
                        capture.write(body);
                        capture.end();
                    };
                    const requestId = read => read('x-request-id') || read('openai-request-id') || read('request-id') || '';
                    const readFetchError = async (response, path, method) => {
                        const id = requestId(name => response.headers.get(name));
                        const capture = splitResponse(path, method, response.status, id);
                        let reader;
                        try {
                            const copy = response.clone();
                            if (copy.body) {
                                reader = copy.body.getReader();
                                const decoder = new TextDecoder();
                                while (true) {
                                    let timer;
                                    let read;
                                    try {
                                        read = await Promise.race([reader.read(), new Promise((_, reject) => {
                                            timer = setTimeout(() => reject(new Error('body-read-timeout')), 4000);
                                        })]);
                                    } finally { clearTimeout(timer); }
                                    if (read.done) break;
                                    capture.write(decoder.decode(read.value, {stream:true}));
                                }
                                capture.write(decoder.decode());
                            }
                            capture.end();
                        } catch (error) {
                            capture.end(error?.message === 'body-read-timeout' ? 'read_timeout' : 'read_failed');
                        } finally {
                            if (reader) void reader.cancel().catch(() => {});
                        }
                    };
                    if (typeof window.fetch === 'function') {
                        const nativeFetch = window.fetch;
                        window.fetch = function (...args) {
                            const promise = Reflect.apply(nativeFetch, this, args);
                            try {
                                const input = args[0];
                                const path = pathOf(input?.url ?? input);
                                if (path) {
                                    const method = String(args[1]?.method ?? input?.method ?? 'GET');
                                    void promise.then(response => {
                                        if (response.status >= 400 && response.status < 600) {
                                            if (response.url && !pathOf(response.url)) return;
                                            return readFetchError(response, path, method);
                                        }
                                    }, error => report(path, method, 0, '', error?.name === 'AbortError' ? 'aborted' : 'network_error'))
                                        .catch(() => {});
                                }
                            } catch (_) {}
                            return promise;
                        };
                    }
                    const xhr = window.XMLHttpRequest?.prototype;
                    if (!xhr) return;
                    const {open, send} = xhr;
                    const requests = new WeakMap();
                    xhr.open = function (...args) {
                        const result = Reflect.apply(open, this, args);
                        const path = pathOf(args[1]);
                        if (path) requests.set(this, {path, method: String(args[0])});
                        else requests.delete(this);
                        return result;
                    };
                    xhr.send = function (...args) {
                        const request = requests.get(this);
                        if (!request) return Reflect.apply(send, this, args);
                        const observe = () => {
                            const {path, method} = request;
                            const code = this.status;
                            if (!code) {
                                report(path, method, 0, '', 'network_error_or_aborted');
                                return;
                            }
                            if (code < 400 || code >= 600 || (this.responseURL && !pathOf(this.responseURL))) return;
                            let id = '';
                            try {
                                id = requestId(name => this.getResponseHeader(name));
                                if (!this.responseType || this.responseType === 'text') {
                                    reportText(path, method, code, id, this.responseText);
                                } else if (this.responseType === 'json' && this.response !== null) {
                                    reportText(path, method, code, id, JSON.stringify(this.response));
                                } else {
                                    report(path, method, code, id, 'unavailable_xhr_response_type');
                                }
                            } catch (_) {
                                report(path, method, code, id, 'read_failed');
                            }
                        };
                        this.addEventListener('loadend', observe, {once: true});
                        try {
                            return Reflect.apply(send, this, args);
                        } catch (error) {
                            this.removeEventListener('loadend', observe);
                            report(request.path, request.method, 0, '', 'send_failed');
                            throw error;
                        }
                    };
                })()
                """;
    }
}
