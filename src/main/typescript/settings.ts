const csrfToken =
  document.querySelector<HTMLMetaElement>('meta[name="_csrf"]')?.content ?? '';
const csrfHeader =
  document.querySelector<HTMLMetaElement>('meta[name="_csrf_header"]')
    ?.content ?? '';

const page = document.querySelector<HTMLElement>(
  '[data-gitlab-validation-url][data-toggl-validation-url]',
);

const ENDPOINTS = {
  gitlab: {
    url: page?.dataset.gitlabValidationUrl,
    body: (v: string) => ({ token: v }),
  },
  toggl: {
    url: page?.dataset.togglValidationUrl,
    body: (v: string) => ({ apiKey: v }),
  },
};

const ICONS = {
  loading:
    '<span class="spinner-border spinner-border-sm text-secondary" role="status" aria-hidden="true"></span><span class="visually-hidden">Checking…</span>',
  valid:
    '<i class="bi bi-check-circle-fill text-success" aria-hidden="true"></i><span class="visually-hidden">Valid</span>',
  invalid:
    '<i class="bi bi-x-circle-fill text-danger" aria-hidden="true"></i><span class="visually-hidden">Invalid</span>',
};

function clearStatus(input: HTMLInputElement, statusEl: HTMLElement) {
  input.classList.remove('is-valid', 'is-invalid');
  statusEl.innerHTML = '';
  statusEl.hidden = true;
}

function setStatus(
  input: HTMLInputElement,
  statusEl: HTMLElement,
  kind: keyof typeof ICONS,
) {
  statusEl.hidden = false;
  statusEl.innerHTML = ICONS[kind];
  input.classList.remove('is-valid', 'is-invalid');
  if (kind === 'valid') input.classList.add('is-valid');
  if (kind === 'invalid') input.classList.add('is-invalid');
}

function makeValidator(
  kind: keyof typeof ENDPOINTS,
  input: HTMLInputElement,
  statusEl: HTMLElement,
) {
  let latestRequest = 0;
  return async function () {
    const value = input.value;
    if (!value) {
      clearStatus(input, statusEl);
      return;
    }
    const cfg = ENDPOINTS[kind];
    if (!cfg.url) return;
    setStatus(input, statusEl, 'loading');
    const requestId = ++latestRequest;
    try {
      const resp = await fetch(cfg.url, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Accept: 'application/json',
          [csrfHeader]: csrfToken,
        },
        body: JSON.stringify(cfg.body(value)),
      });
      if (requestId !== latestRequest) return;
      if (!resp.ok) {
        setStatus(input, statusEl, 'invalid');
        return;
      }
      const data: unknown = await resp.json();
      setStatus(
        input,
        statusEl,
        typeof data === 'object' &&
          data !== null &&
          'valid' in data &&
          data.valid === true
          ? 'valid'
          : 'invalid',
      );
    } catch (err) {
      if (requestId !== latestRequest) return;
      setStatus(input, statusEl, 'invalid');
    }
  };
}

document
  .querySelectorAll<HTMLInputElement>('input[data-validate]')
  .forEach((input) => {
    const kind = input.dataset.validate;
    const statusEl = document.getElementById(input.id + '-status');
    if (
      !statusEl ||
      (kind !== 'gitlab' && kind !== 'toggl') ||
      !ENDPOINTS[kind].url
    )
      return;
    const validate = makeValidator(kind, input, statusEl);
    input.addEventListener('blur', validate);
    input.addEventListener('input', () => clearStatus(input, statusEl));
  });
