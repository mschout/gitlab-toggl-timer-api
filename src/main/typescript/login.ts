function meta(name: string) {
  const el = document.querySelector<HTMLMetaElement>(
    'meta[name="' + name + '"]',
  );
  return el ? el.content : '';
}
const csrfToken = meta('_csrf');
const csrfHeader = meta('_csrf_header');
const btn = document.querySelector<HTMLButtonElement>('[data-passkey-login]');
if (btn) {
  if (!window.PublicKeyCredential || !window.setupLogin) {
    btn.disabled = true;
  } else {
    const headers: Record<string, string> = {};
    if (csrfHeader && csrfToken) headers[csrfHeader] = csrfToken;
    window.setupLogin(headers, '', btn);
  }
}
