document.body.addEventListener('htmx:config:request', function (evt) {
  var token = document.querySelector('meta[name="_csrf"]').content;
  var header = document.querySelector('meta[name="_csrf_header"]').content;
  if (token && header) {
    evt.detail.ctx.request.headers[header] = token;
  }
});

function scheduleAutoDismissAlerts(root) {
  if (!root || !root.querySelectorAll) return;
  var alerts = [];
  if (root.matches && root.matches('[data-auto-dismiss-after]')) alerts.push(root);
  root.querySelectorAll('[data-auto-dismiss-after]').forEach(function (alert) {
    alerts.push(alert);
  });
  alerts.forEach(function (alert) {
    if (alert.dataset.autoDismissScheduled === 'true') return;
    var delay = Number.parseInt(alert.dataset.autoDismissAfter, 10);
    if (!Number.isFinite(delay) || delay < 0) return;
    alert.dataset.autoDismissScheduled = 'true';
    window.setTimeout(function () {
      if (!alert.isConnected) return;
      bootstrap.Alert.getOrCreateInstance(alert).close();
    }, delay);
  });
}

document.addEventListener('DOMContentLoaded', function () {
  scheduleAutoDismissAlerts(document);
});

document.body.addEventListener('htmx:after:swap', function (evt) {
  var ctx = evt.detail && evt.detail.ctx;
  var target = ctx && ctx.target;
  if (typeof target === 'string') target = document.querySelector(target);
  scheduleAutoDismissAlerts(target || document);
});
