const fs = require('node:fs');
const assert = require('node:assert/strict');
const step = fs.readFileSync(0, 'utf8');
for (const expected of [
  'single line',
  'line <img src=x onerror=evil()> &\n\n  한글\t"quoted"\n마지막',
  '한글 <>&😀\n'.repeat(600).trim(),
]) {
  for (const policy of ['native', 'unsupported', 'throws', 'textarea']) {
    const calls = [];
    let html = '';
    const paragraph = {
      set textContent(value) { composer.textContent = value; },
    };
    const doc = {
      defaultView: {getSelection: () => ({removeAllRanges() {}, addRange() {}})},
      createRange: () => ({selectNodeContents() {}, collapse() {}}),
      execCommand(kind, _, value) {
        calls.push(kind);
        if (kind === 'insertHTML' && policy === 'throws') throw new Error('unsupported');
        if (kind === 'insertHTML' && policy === 'unsupported') return false;
        html = value;
        composer.textContent = expected;
        return true;
      },
    };
    const composer = {
      ownerDocument: doc, focus() {}, querySelector: () => paragraph,
      childNodes: [paragraph], firstChild: paragraph,
      dispatchEvent: () => true, textContent: '',
    };
    if (policy === 'textarea') composer.value = '';
    const raw = () => policy === 'textarea' ? composer.value : composer.textContent;
    const canonical = s => s.trim();
    const domState = {phase: 'inserting', reverts: 0};
    const diag = () => ({});
    const result = (status, detail, diagnostics) => ({status, detail, diagnostics});
    const actual = eval('(() => {' + step + '})()');
    assert.equal(actual.status, 'RETRY');
    assert.equal(raw(), expected);
    assert.equal(domState.phase, 'inputting');
    if (policy === 'textarea') assert.deepEqual(calls, []);
    else if (expected.includes('\n')) {
      assert.deepEqual(calls, ['insertHTML']);
      if (policy === 'native') {
        assert(!html.includes('<img'));
        assert(html.includes('&lt;'));
        if (expected.startsWith('line ')) {
          assert.equal(html, '<p>line &lt;img src=x onerror=evil()&gt; &amp;</p><p><br></p><p>  한글\t"quoted"</p><p>마지막</p>');
        }
      }
    } else assert.deepEqual(calls, ['insertText']);
  }
}
console.log('PASS: native/unsupported/throwing/textarea transport, escaped multiline, long Korean/emoji input');
