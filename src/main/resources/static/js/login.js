(function () {
  function meta(name) {
    const el = document.querySelector('meta[name="' + name + '"]');
    return el ? el.content : '';
  }
  const csrfToken = meta('_csrf');
  const csrfHeader = meta('_csrf_header');
  const btn = document.querySelector('[data-passkey-login]');
  if (!btn) return;
  if (!window.PublicKeyCredential || !window.setupLogin) {
    btn.disabled = true;
    return;
  }
  const headers = {};
  if (csrfHeader && csrfToken) headers[csrfHeader] = csrfToken;
  window.setupLogin(headers, '', btn);
})();
