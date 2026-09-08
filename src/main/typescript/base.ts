document.body.addEventListener('htmx:config:request', function (evt) {
  const token =
    document.querySelector<HTMLMetaElement>('meta[name="_csrf"]')?.content;
  const header = document.querySelector<HTMLMetaElement>(
    'meta[name="_csrf_header"]',
  )?.content;
  if (token && header) {
    evt.detail.ctx.request.headers[header] = token;
  }
});

function scheduleAutoDismissAlerts(root: Document | Element | null) {
  if (!root) return;
  const alerts: HTMLElement[] = [];
  if (root instanceof HTMLElement && root.matches('[data-auto-dismiss-after]'))
    alerts.push(root);
  root
    .querySelectorAll<HTMLElement>('[data-auto-dismiss-after]')
    .forEach(function (alert) {
      alerts.push(alert);
    });
  alerts.forEach(function (alert) {
    if (alert.dataset.autoDismissScheduled === 'true') return;
    const delay = Number.parseInt(alert.dataset.autoDismissAfter ?? '', 10);
    if (!Number.isFinite(delay) || delay < 0) return;
    alert.dataset.autoDismissScheduled = 'true';
    window.setTimeout(function () {
      if (!alert.isConnected) return;
      window.bootstrap.Alert.getOrCreateInstance(alert).close();
    }, delay);
  });
}

document.addEventListener('DOMContentLoaded', function () {
  scheduleAutoDismissAlerts(document);
});

document.body.addEventListener('htmx:after:swap', function (evt) {
  const ctx = evt.detail && evt.detail.ctx;
  let target: Element | string | null = ctx.target;
  if (typeof target === 'string') target = document.querySelector(target);
  scheduleAutoDismissAlerts(target || document);
});

// Load one candidate for both avatars, so their fallback state stays in sync.
function initializeUserAvatars() {
  const avatars = Array.from(
    document.querySelectorAll<HTMLImageElement>(
      '[data-avatar-primary], [data-avatar-gravatar]',
    ),
  );
  const firstAvatar = avatars[0];
  if (!firstAvatar) return;
  const candidates = [
    firstAvatar.dataset.avatarPrimary,
    firstAvatar.dataset.avatarGravatar,
  ].filter(function (url, index, urls): url is string {
    return (
      typeof url === 'string' && url.length > 0 && urls.indexOf(url) === index
    );
  });

  function tryNext() {
    const url = candidates.shift();
    if (!url) return;
    const image = new Image();
    let settled = false;
    const complete = (success: boolean) => {
      if (settled) return;
      settled = true;
      if (!success) {
        tryNext();
        return;
      }
      avatars.forEach(function (avatar) {
        avatar.onload = function () {
          avatar.classList.remove('d-none');
        };
        avatar.onerror = function () {
          avatar.classList.add('d-none');
        };
        avatar.src = url;
        if (avatar.complete && avatar.naturalWidth > 0)
          avatar.classList.remove('d-none');
      });
    };
    image.referrerPolicy = 'no-referrer';
    image.onload = function () {
      complete(true);
    };
    image.onerror = function () {
      complete(false);
    };
    image.src = url;
    if (image.complete) complete(image.naturalWidth > 0);
  }

  tryNext();
}

initializeUserAvatars();
