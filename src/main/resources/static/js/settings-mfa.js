(function () {
  const copyBtn = document.getElementById('recovery-copy-btn');
  const codesEl = document.getElementById('recovery-codes');
  if (!copyBtn || !codesEl) return;
  const label = document.getElementById('recovery-copy-label');
  copyBtn.addEventListener('click', async () => {
    const codes = Array.from(codesEl.querySelectorAll('div'))
      .map(el => el.textContent.trim())
      .filter(Boolean)
      .join('\n');
    try {
      await navigator.clipboard.writeText(codes);
      if (label) label.textContent = 'Copied!';
      setTimeout(() => { if (label) label.textContent = 'Copy to clipboard'; }, 2000);
    } catch (e) {
      if (label) label.textContent = 'Copy failed';
      console.error('clipboard:', e);
    }
  });
})();

(function () {
  function meta(name) {
    const el = document.querySelector('meta[name="' + name + '"]');
    return el ? el.content : '';
  }
  const csrfToken = meta('_csrf');
  const csrfHeader = meta('_csrf_header');

  function b64urlDecode(b) {
    const s = b.replace(/-/g, '+').replace(/_/g, '/');
    const pad = s.length % 4 === 0 ? s : s + '='.repeat(4 - (s.length % 4));
    const bin = atob(pad);
    const buf = new Uint8Array(bin.length);
    for (let i = 0; i < bin.length; i++) buf[i] = bin.charCodeAt(i);
    return buf.buffer;
  }
  function b64urlEncode(buf) {
    const b = btoa(String.fromCharCode.apply(null, new Uint8Array(buf)));
    return b.replace(/=/g, '').replace(/\+/g, '-').replace(/\//g, '_');
  }

  function showError(msg) {
    const errorEl = document.getElementById('passkey-error');
    if (!errorEl) return;
    errorEl.textContent = msg;
    errorEl.classList.remove('d-none');
    console.error('passkey:', msg);
  }

  const btn = document.getElementById('passkey-register-btn');
  if (!btn) return;
  if (!window.PublicKeyCredential) {
    btn.disabled = true;
    showError('This browser does not support WebAuthn / passkeys.');
    return;
  }

  btn.addEventListener('click', async () => {
    const errorEl = document.getElementById('passkey-error');
    if (errorEl) errorEl.classList.add('d-none');
    const labelInput = document.getElementById('passkey-label');
    const label = (labelInput && labelInput.value || '').trim();
    if (!label) {
      showError('Please give the passkey a label.');
      return;
    }
    try {
      const headers = { 'Content-Type': 'application/json' };
      if (csrfHeader && csrfToken) headers[csrfHeader] = csrfToken;
      const optsResp = await fetch('/webauthn/register/options', { method: 'POST', headers });
      if (!optsResp.ok) throw new Error('register/options returned HTTP ' + optsResp.status);
      const opts = await optsResp.json();
      opts.challenge = b64urlDecode(opts.challenge);
      opts.user.id = b64urlDecode(opts.user.id);
      opts.excludeCredentials = (opts.excludeCredentials || []).map(function (c) { return Object.assign({}, c, { id: b64urlDecode(c.id) }); });
      const cred = await navigator.credentials.create({ publicKey: opts });
      const body = {
        publicKey: {
          credential: {
            id: cred.id,
            rawId: b64urlEncode(cred.rawId),
            response: {
              attestationObject: b64urlEncode(cred.response.attestationObject),
              clientDataJSON: b64urlEncode(cred.response.clientDataJSON),
              transports: cred.response.getTransports ? cred.response.getTransports() : [],
            },
            type: cred.type,
            clientExtensionResults: cred.getClientExtensionResults(),
            authenticatorAttachment: cred.authenticatorAttachment,
          },
          label: label,
        }
      };
      const regResp = await fetch('/webauthn/register', { method: 'POST', headers, body: JSON.stringify(body) });
      if (!regResp.ok) throw new Error('register returned HTTP ' + regResp.status);
      window.location.reload();
    } catch (e) {
      showError(e.message || String(e));
    }
  });
})();
