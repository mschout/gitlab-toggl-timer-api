(function () {
  var STORAGE_KEY = 'gitlabTogglTimer.timer.selectedClient';
  var ISSUE_URL_KEY = 'gitlabTogglTimer.timer.issueUrl';
  var restoreStartEditorFocusAfterSwap = false;

  function getWorkspaceId() {
    var el = document.getElementById('workspaceId');
    return el && el.value ? el.value : null;
  }

  function readStored() {
    try {
      var raw = localStorage.getItem(STORAGE_KEY);
      return raw ? JSON.parse(raw) : null;
    } catch (e) {
      return null;
    }
  }

  function writeStored(workspaceId, clientId) {
    try {
      localStorage.setItem(
        STORAGE_KEY,
        JSON.stringify({ workspaceId: String(workspaceId), clientId: String(clientId) })
      );
    } catch (e) {}
  }

  function clearStored() {
    try {
      localStorage.removeItem(STORAGE_KEY);
    } catch (e) {}
  }

  function restoreClientSelection() {
    var clientEl = document.getElementById('clientId');
    if (!clientEl) return;
    if (clientEl.value) return;
    var workspaceId = getWorkspaceId();
    if (!workspaceId) return;
    var stored = readStored();
    if (!stored || stored.workspaceId !== String(workspaceId)) return;
    var clientId = String(stored.clientId);
    if (clientEl.tagName === 'SELECT') {
      for (var i = 0; i < clientEl.options.length; i++) {
        if (clientEl.options[i].value === clientId) {
          clientEl.value = clientId;
          return;
        }
      }
    } else {
      clientEl.value = clientId;
    }
  }

  function restoreIssueUrl() {
    var el = document.getElementById('issueUrl');
    if (!el || el.value) return;
    try {
      var stored = localStorage.getItem(ISSUE_URL_KEY);
      if (stored) el.value = stored;
    } catch (e) {}
  }

  function clearIssueUrl() {
    var input = document.getElementById('issueUrl');
    if (input) input.value = '';
    try {
      localStorage.removeItem(ISSUE_URL_KEY);
    } catch (e) {}
  }

  function padElapsedPart(value) {
    return value < 10 ? '0' + value : '' + value;
  }

  var basePageTitle = document.title;

  function elapsedSeconds(timer) {
    if (!timer) return null;
    var startedAt = new Date(timer.getAttribute('data-started-at'));
    if (isNaN(startedAt.getTime())) return null;
    return Math.max(0, Math.floor((Date.now() - startedAt.getTime()) / 1000));
  }

  function formatElapsedTitle(seconds) {
    var hours = Math.floor(seconds / 3600);
    var minutes = Math.floor((seconds % 3600) / 60);
    var remainingSeconds = seconds % 60;
    if (hours === 0) return minutes + ':' + padElapsedPart(remainingSeconds);
    return hours + ':' + padElapsedPart(minutes) + ':' + padElapsedPart(remainingSeconds);
  }

  function renderPageTitle(runningTimer) {
    var seconds = elapsedSeconds(runningTimer);
    document.title =
      seconds === null ? basePageTitle : formatElapsedTitle(seconds) + ' • ' + basePageTitle;
  }

  function renderElapsedTime(el) {
    var seconds = elapsedSeconds(el);
    if (seconds === null) return;
    var hours = Math.floor(seconds / 3600);
    var minutes = Math.floor((seconds % 3600) / 60);
    var remainingSeconds = seconds % 60;
    el.textContent =
      padElapsedPart(hours) +
      ':' +
      padElapsedPart(minutes) +
      ':' +
      padElapsedPart(remainingSeconds);
  }

  function updateRunningSplitEligibility() {
    document
      .querySelectorAll('.running-timer-split-trigger[data-enable-at]')
      .forEach(function (trigger) {
        var enableAt = Number(trigger.getAttribute('data-enable-at'));
        if (!Number.isFinite(enableAt) || Date.now() < enableAt) return;
        trigger.disabled = false;
        trigger.removeAttribute('aria-describedby');
        trigger.removeAttribute('data-enable-at');
      });
  }

  function formatTotalTime(seconds) {
    var hours = Math.floor(seconds / 3600);
    var minutes = Math.floor((seconds % 3600) / 60);
    var remainingSeconds = seconds % 60;
    return hours + ':' + padElapsedPart(minutes) + ':' + padElapsedPart(remainingSeconds);
  }

  function renderTimeTotals() {
    var runningTimer = document.querySelector('.js-elapsed-time');
    var startedAt =
      runningTimer && new Date(runningTimer.getAttribute('data-started-at'));
    var startedAtMs = startedAt && startedAt.getTime();
    var elapsedSeconds =
      startedAtMs && !isNaN(startedAtMs)
        ? Math.max(0, Math.floor((Date.now() - startedAtMs) / 1000))
        : 0;

    document.querySelectorAll('.js-time-total').forEach(function (total) {
      var seconds = Number(total.getAttribute('data-base-seconds')) || 0;
      var periodStart = new Date(total.getAttribute('data-period-start')).getTime();
      var periodEnd = new Date(total.getAttribute('data-period-end')).getTime();
      if (
        startedAtMs &&
        !isNaN(periodStart) &&
        !isNaN(periodEnd) &&
        startedAtMs >= periodStart &&
        startedAtMs < periodEnd
      ) {
        seconds += elapsedSeconds;
      }
      total.textContent = formatTotalTime(seconds);
    });
  }

  function startElapsedTimers() {
    if (window.__togglElapsedIntervalId) {
      clearInterval(window.__togglElapsedIntervalId);
      window.__togglElapsedIntervalId = null;
    }
    var timers = document.querySelectorAll('.js-elapsed-time');
    timers.forEach(renderElapsedTime);
    updateRunningSplitEligibility();
    renderTimeTotals();
    renderPageTitle(timers[0]);
    if (!timers.length) return;
    window.__togglElapsedIntervalId = setInterval(function () {
      var currentTimers = document.querySelectorAll('.js-elapsed-time');
      if (!currentTimers.length) {
        clearInterval(window.__togglElapsedIntervalId);
        window.__togglElapsedIntervalId = null;
        renderPageTitle(null);
        return;
      }
      currentTimers.forEach(renderElapsedTime);
      updateRunningSplitEligibility();
      renderTimeTotals();
      renderPageTitle(currentTimers[0]);
    }, 1000);
  }

  function parseLocalDate(value) {
    var parts = value && value.split('-').map(Number);
    if (!parts || parts.length !== 3 || parts.some(Number.isNaN)) return null;
    return new Date(parts[0], parts[1] - 1, parts[2]);
  }

  function formatLocalDate(date) {
    return (
      date.getFullYear() +
      '-' +
      String(date.getMonth() + 1).padStart(2, '0') +
      '-' +
      String(date.getDate()).padStart(2, '0')
    );
  }

  function formatStartDate(date, today) {
    var isoDate = formatLocalDate(date);
    if (isoDate === today) return 'Today';
    return (
      String(date.getMonth() + 1).padStart(2, '0') +
      '/' +
      String(date.getDate()).padStart(2, '0')
    );
  }

  function startEditorDialogs(root) {
    if (!root || !root.querySelectorAll) return [];
    var dialogs = [];
    if (root.matches && root.matches('.running-timer-start-dialog')) dialogs.push(root);
    root.querySelectorAll('.running-timer-start-dialog').forEach(function (dialog) {
      dialogs.push(dialog);
    });
    return dialogs;
  }

  function initializeStartEditor(dialog) {
    if (!dialog || dialog._runningTimerDatepicker || !window.AirDatepicker) return;
    var calendar = dialog.querySelector('.running-timer-start-calendar');
    var dateDisplay = dialog.querySelector('.running-timer-start-date-display');
    var dateInput = dialog.querySelector('.running-timer-start-date');
    var selectedDate = parseLocalDate(dateInput.value);
    var today = parseLocalDate(dialog.dataset.today);
    if (!calendar || !dateDisplay || !dateInput || !selectedDate || !today) return;

    dialog._runningTimerDatepicker = new AirDatepicker(calendar, {
      inline: true,
      locale: {
        days: ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday'],
        daysShort: ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'],
        daysMin: ['Su', 'Mo', 'Tu', 'We', 'Th', 'Fr', 'Sa'],
        months: [
          'January', 'February', 'March', 'April', 'May', 'June',
          'July', 'August', 'September', 'October', 'November', 'December'
        ],
        monthsShort: [
          'Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun',
          'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'
        ],
        today: 'Today',
        clear: 'Clear',
        dateFormat: 'MM/dd/yyyy',
        timeFormat: 'hh:mm aa',
        firstDay: 1
      },
      selectedDates: [selectedDate],
      startDate: selectedDate,
      maxDate: today,
      firstDay: 1,
      fixedHeight: true,
      showOtherMonths: false,
      selectOtherMonths: false,
      toggleSelected: false,
      autoClose: false,
      keyboardNav: true,
      altField: '#' + dateInput.id,
      altFieldDateFormat: 'yyyy-MM-dd',
      navTitles: {
        days: 'MMMM yyyy',
        months: 'yyyy',
        years: 'yyyy1 - yyyy2'
      },
      onRenderCell: function (cell) {
        if (cell.cellType !== 'day') return;
        if (formatLocalDate(cell.date) === dialog.dataset.today) {
          return { classes: 'running-timer-start-today' };
        }
      },
      onSelect: function (selection) {
        if (!selection.date) return;
        dateInput.value = formatLocalDate(selection.date);
        dateDisplay.value = formatStartDate(selection.date, dialog.dataset.today);
      }
    });
  }

  function initializeStartEditors(root) {
    startEditorDialogs(root).forEach(function (dialog) {
      initializeStartEditor(dialog);
      if (dialog.dataset.open === 'true' && !dialog.open) {
        dialog.showModal();
        var timeInput = dialog.querySelector('.running-timer-start-time');
        if (timeInput) {
          timeInput.focus();
          timeInput.select();
        }
      }
    });
  }

  function disposeStartEditors(root) {
    startEditorDialogs(root).forEach(function (dialog) {
      if (!dialog._runningTimerDatepicker) return;
      dialog._runningTimerDatepicker.destroy();
      dialog._runningTimerDatepicker = null;
    });
  }

  function resetStartEditor(dialog) {
    if (!dialog) return;
    var dateInput = dialog.querySelector('.running-timer-start-date');
    var dateDisplay = dialog.querySelector('.running-timer-start-date-display');
    var timeInput = dialog.querySelector('.running-timer-start-time');
    var originalDate = parseLocalDate(dialog.dataset.initialStartDate);
    if (dateInput) dateInput.value = dialog.dataset.initialStartDate;
    if (dateDisplay && originalDate) {
      dateDisplay.value = formatStartDate(originalDate, dialog.dataset.today);
    }
    if (timeInput) timeInput.value = dialog.dataset.initialStartTime;
    if (dialog._runningTimerDatepicker && originalDate) {
      dialog._runningTimerDatepicker.selectDate(originalDate, { silent: true });
      dialog._runningTimerDatepicker.setViewDate(originalDate);
    }
  }

  function closeStartEditor(dialog) {
    if (!dialog) return;
    resetStartEditor(dialog);
    dialog.close();
    var trigger = document.querySelector(
      '.running-timer-elapsed-trigger[aria-controls="' + dialog.id + '"]'
    );
    if (trigger) trigger.focus();
  }

  function openStartEditor(trigger) {
    if (!trigger) return;
    var dialogId = trigger.getAttribute('aria-controls');
    var dialog = dialogId && document.getElementById(dialogId);
    if (!dialog) return;
    resetStartEditor(dialog);
    initializeStartEditor(dialog);
    if (!dialog.open) dialog.showModal();
    var timeInput = dialog.querySelector('.running-timer-start-time');
    if (timeInput) {
      timeInput.focus();
      timeInput.select();
    }
  }

  document.addEventListener('DOMContentLoaded', function () {
    restoreClientSelection();
    restoreIssueUrl();
    startElapsedTimers();
    initializeStartEditors(document);
    document.querySelectorAll('.time-entry-split-dialog').forEach(initializeSplitDialog);
  });

  document.addEventListener('cancel', function (evt) {
    if (!evt.target.matches || !evt.target.matches('.running-timer-start-dialog')) return;
    evt.preventDefault();
    closeStartEditor(evt.target);
  });

  document.addEventListener('submit', function (evt) {
    if (evt.target.matches && evt.target.matches('.running-timer-start-form')) {
      restoreStartEditorFocusAfterSwap = true;
    }
  });

  document.body.addEventListener('issueUrlConsumed', clearIssueUrl);

  document.addEventListener('input', function (evt) {
    var target = evt.target;
    if (!target || !target.matches) return;
    if (target.matches('.time-entry-split-slider')) {
      renderSplitDialog(target.closest('.time-entry-split-dialog'), false);
      return;
    }
    if (target.matches('.time-entry-split-manual-input')) {
      applyManualSplit(target.closest('.time-entry-split-dialog'));
    }
  });

  document.addEventListener(
    'toggle',
    function (evt) {
      if (!evt.target.matches || !evt.target.matches('.time-entry-split-manual')) return;
      var dialog = evt.target.closest('.time-entry-split-dialog');
      if (evt.target.open) {
        renderSplitDialog(dialog, false);
        var input = dialog.querySelector('.time-entry-split-manual-input');
        if (input) input.focus();
      } else {
        setManualSplitError(dialog, '');
        renderSplitDialog(dialog, false);
      }
    },
    true
  );

  document.addEventListener('change', function (evt) {
    var target = evt.target;
    if (!target) return;
    if (target.matches && target.matches('.time-entry-split-mode-input')) {
      renderSplitDialog(target.closest('.time-entry-split-dialog'), false);
      return;
    }
    if (target.matches && target.matches('.js-stopped-project')) {
      var toolbar = target.closest('.running-timer-toolbar');
      var selected = target.options[target.selectedIndex];
      var projectColor = selected && selected.getAttribute('data-project-color');
      if (toolbar && projectColor) {
        toolbar.style.setProperty('--project-color', projectColor);
      } else if (toolbar) {
        toolbar.style.removeProperty('--project-color');
      }
      return;
    }
    if (!target.id) return;
    if (target.id === 'clientId') {
      var workspaceId = getWorkspaceId();
      if (workspaceId && target.value) {
        writeStored(workspaceId, target.value);
      } else {
        clearStored();
      }
    } else if (target.id === 'workspaceId') {
      clearStored();
    } else if (target.id === 'issueUrl') {
      try {
        if (target.value) {
          localStorage.setItem(ISSUE_URL_KEY, target.value);
        } else {
          localStorage.removeItem(ISSUE_URL_KEY);
        }
      } catch (e) {}
    }
  });

  document.addEventListener('click', function (evt) {
    var startTrigger =
      evt.target.closest && evt.target.closest('.running-timer-elapsed-trigger');
    if (startTrigger) {
      openStartEditor(startTrigger);
      return;
    }

    var startCancel =
      evt.target.closest && evt.target.closest('.running-timer-start-cancel');
    if (startCancel) {
      closeStartEditor(startCancel.closest('.running-timer-start-dialog'));
      return;
    }

    if (evt.target.matches && evt.target.matches('.running-timer-start-dialog')) {
      var startDialogRect = evt.target.getBoundingClientRect();
      var outsideStartDialog =
        evt.clientX < startDialogRect.left ||
        evt.clientX > startDialogRect.right ||
        evt.clientY < startDialogRect.top ||
        evt.clientY > startDialogRect.bottom;
      if (outsideStartDialog) closeStartEditor(evt.target);
      return;
    }

    var copyDescription =
      evt.target.closest && evt.target.closest('.time-entry-copy-description');
    if (copyDescription) {
      copyEntryDescription(copyDescription);
      return;
    }

    var splitTrigger =
      evt.target.closest && evt.target.closest('.time-entry-split-trigger');
    if (splitTrigger) {
      openSplitDialog(splitTrigger);
      return;
    }

    var splitClose =
      evt.target.closest && evt.target.closest('.time-entry-split-close');
    if (splitClose) {
      var splitDialog = splitClose.closest('.time-entry-split-dialog');
      if (splitDialog) splitDialog.close();
      return;
    }

    if (evt.target.matches && evt.target.matches('.time-entry-split-dialog')) {
      var splitDialogRect = evt.target.getBoundingClientRect();
      var outsideSplitDialog =
        evt.clientX < splitDialogRect.left ||
        evt.clientX > splitDialogRect.right ||
        evt.clientY < splitDialogRect.top ||
        evt.clientY > splitDialogRect.bottom;
      if (outsideSplitDialog) evt.target.close();
      return;
    }

    var deleteTrigger =
      evt.target.closest && evt.target.closest('.time-entry-delete-trigger');
    if (deleteTrigger) {
      openDeleteDialog(deleteTrigger);
      return;
    }

    var deleteClose =
      evt.target.closest && evt.target.closest('.time-entry-delete-close');
    if (deleteClose) {
      var deleteDialog = deleteClose.closest('.time-entry-delete-dialog');
      if (deleteDialog) deleteDialog.close();
      return;
    }

    if (
      evt.target.matches &&
      evt.target.matches('.time-entry-delete-dialog')
    ) {
      var deleteDialogRect = evt.target.getBoundingClientRect();
      var outsideDeleteDialog =
        evt.clientX < deleteDialogRect.left ||
        evt.clientX > deleteDialogRect.right ||
        evt.clientY < deleteDialogRect.top ||
        evt.clientY > deleteDialogRect.bottom;
      if (outsideDeleteDialog) evt.target.close();
      return;
    }

    var projectTrigger =
      evt.target.closest && evt.target.closest('.time-entry-project-trigger');
    if (projectTrigger) {
      openProjectPicker(projectTrigger);
      return;
    }

    var projectClose =
      evt.target.closest && evt.target.closest('.time-entry-project-close');
    if (projectClose) {
      var closeDialog = projectClose.closest('.time-entry-project-dialog');
      if (closeDialog) closeDialog.close();
      return;
    }

    if (
      evt.target.matches &&
      evt.target.matches('.time-entry-project-dialog')
    ) {
      var dialogRect = evt.target.getBoundingClientRect();
      var outsideDialog =
        evt.clientX < dialogRect.left ||
        evt.clientX > dialogRect.right ||
        evt.clientY < dialogRect.top ||
        evt.clientY > dialogRect.bottom;
      if (outsideDialog) evt.target.close();
      return;
    }

    var descriptionInput =
      evt.target.closest && evt.target.closest('.time-entry-description-editor');
    if (descriptionInput) {
      beginDescriptionEdit(descriptionInput);
      return;
    }

    var btn = evt.target.closest && evt.target.closest('#issueUrlClear');
    if (!btn) return;
    clearIssueUrl();
    var input = document.getElementById('issueUrl');
    if (!input) return;
    input.focus();
  });

  function openProjectPicker(trigger) {
    if (!trigger) return;
    var dialogId = trigger.getAttribute('aria-controls');
    var dialog = dialogId && document.getElementById(dialogId);
    if (!dialog) return;
    if (!dialog.open) dialog.showModal();
    var search = dialog.querySelector('.time-entry-project-search');
    if (!search) return;
    search.focus();
    htmx.trigger(search, 'project-search');
  }

  function copyEntryDescription(button) {
    if (!button || button.disabled) return;
    var label = button.querySelector('.time-entry-copy-label');
    var description = button.getAttribute('data-description') || '';
    var clipboard = navigator.clipboard;
    var copyRequest =
      clipboard && clipboard.writeText
        ? clipboard.writeText(description)
        : Promise.reject(new Error('Clipboard API unavailable'));

    copyRequest
      .then(function () {
        if (label) label.textContent = 'Copied!';
        window.setTimeout(function () {
          if (label) label.textContent = 'Copy description';
          hideEntryActionsMenu(button);
        }, 1200);
      })
      .catch(function () {
        if (label) label.textContent = 'Copy failed';
        window.setTimeout(function () {
          if (label) label.textContent = 'Copy description';
        }, 2000);
      });
  }

  function hideEntryActionsMenu(element) {
    var actions = element && element.closest('.time-entry-actions');
    var trigger = actions && actions.querySelector('.time-entry-actions-trigger');
    if (trigger && window.bootstrap) {
      bootstrap.Dropdown.getOrCreateInstance(trigger).hide();
    }
  }

  function openDeleteDialog(trigger) {
    if (!trigger) return;
    var dialogId = trigger.getAttribute('aria-controls');
    var dialog = dialogId && document.getElementById(dialogId);
    if (!dialog) return;
    hideEntryActionsMenu(trigger);
    if (!dialog.open) dialog.showModal();
  }

  function openSplitDialog(trigger) {
    if (!trigger) return;
    var dialogId = trigger.getAttribute('aria-controls');
    var dialog = dialogId && document.getElementById(dialogId);
    if (!dialog) return;
    hideEntryActionsMenu(trigger);
    initializeSplitDialog(dialog);
    if (!dialog.open) dialog.showModal();
  }

  function initializeSplitDialog(dialog) {
    if (!dialog) return;
    renderSplitDialog(dialog, false);
  }

  function splitMode(dialog) {
    var selected = dialog.querySelector('.time-entry-split-mode-input:checked');
    return selected ? selected.value : 'elapsed';
  }

  function formatSplitDuration(seconds) {
    var safeSeconds = Math.max(0, Math.floor(seconds));
    var hours = Math.floor(safeSeconds / 3600);
    var minutes = Math.floor((safeSeconds % 3600) / 60);
    var remainingSeconds = safeSeconds % 60;
    return (
      String(hours).padStart(2, '0') +
      ':' +
      String(minutes).padStart(2, '0') +
      ':' +
      String(remainingSeconds).padStart(2, '0')
    );
  }

  function formatSplitClock(dialog, offset, twentyFourHour) {
    var startMilliseconds = Number(dialog.dataset.startEpochMilliseconds);
    var instant = new Date(startMilliseconds + offset * 1000);
    var options = {
      timeZone: dialog.dataset.timeZone,
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit'
    };
    if (twentyFourHour) {
      options.hourCycle = 'h23';
    } else {
      options.hour = 'numeric';
    }
    return new Intl.DateTimeFormat('en-US', options).format(instant);
  }

  function renderSplitDialog(dialog, preserveManualValue) {
    if (!dialog) return;
    var slider = dialog.querySelector('.time-entry-split-slider');
    var offset = Number(slider.value);
    var duration = Number(dialog.dataset.durationSeconds);
    dialog.querySelector('.time-entry-split-clock').textContent =
      formatSplitClock(dialog, offset, false);
    dialog.querySelector('.time-entry-split-first-duration').textContent =
      formatSplitDuration(offset);
    dialog.querySelector('.time-entry-split-second-duration').textContent =
      formatSplitDuration(duration - offset);

    var manual = dialog.querySelector('.time-entry-split-manual');
    if (!manual || !manual.open || preserveManualValue) return;
    var input = dialog.querySelector('.time-entry-split-manual-input');
    input.value =
      splitMode(dialog) === 'elapsed'
        ? formatSplitDuration(offset)
        : formatSplitClock(dialog, offset, true);
    var help = dialog.querySelector('.time-entry-split-manual-help');
    help.textContent =
      splitMode(dialog) === 'elapsed'
        ? 'Enter elapsed time after the start.'
        : 'Enter a 24-hour clock time in ' + dialog.dataset.timeZone + '.';
    setManualSplitError(dialog, '');
  }

  function parseElapsedSplit(value) {
    var match = /^(\d{2,}):([0-5]\d):([0-5]\d)$/.exec(value.trim());
    if (!match) return null;
    return Number(match[1]) * 3600 + Number(match[2]) * 60 + Number(match[3]);
  }

  function parseClockSplit(value) {
    var match = /^([01]\d|2[0-3]):([0-5]\d):([0-5]\d)$/.exec(value.trim());
    if (!match) return null;
    return Number(match[1]) * 3600 + Number(match[2]) * 60 + Number(match[3]);
  }

  function applyManualSplit(dialog) {
    if (!dialog) return;
    var input = dialog.querySelector('.time-entry-split-manual-input');
    var mode = splitMode(dialog);
    var duration = Number(dialog.dataset.durationSeconds);
    var offset;
    if (mode === 'elapsed') {
      offset = parseElapsedSplit(input.value);
      if (offset === null) {
        setManualSplitError(dialog, 'Use HH:MM:SS, for example 00:29:00.');
        return;
      }
    } else {
      var clockSeconds = parseClockSplit(input.value);
      if (clockSeconds === null) {
        setManualSplitError(dialog, 'Use a 24-hour time such as 09:42:00.');
        return;
      }
      if (
        duration >= 86400 ||
        dialog.dataset.startOffsetSeconds !== dialog.dataset.stopOffsetSeconds
      ) {
        setManualSplitError(
          dialog,
          'This clock time may occur more than once. Use Elapsed or the slider.'
        );
        return;
      }
      offset = clockSeconds - Number(dialog.dataset.startLocalSecondOfDay);
      if (offset < 0) offset += 86400;
    }

    if (offset < 1 || offset >= duration) {
      setManualSplitError(
        dialog,
        'Choose a time that leaves at least one second on each side.'
      );
      return;
    }
    dialog.querySelector('.time-entry-split-slider').value = String(offset);
    setManualSplitError(dialog, '');
    renderSplitDialog(dialog, true);
  }

  function setManualSplitError(dialog, message) {
    if (!dialog) return;
    var input = dialog.querySelector('.time-entry-split-manual-input');
    var error = dialog.querySelector('.time-entry-split-manual-error');
    var confirm = dialog.querySelector('.time-entry-split-confirm');
    input.classList.toggle('is-invalid', Boolean(message));
    input.setAttribute('aria-invalid', message ? 'true' : 'false');
    error.textContent = message;
    if (message) error.style.display = 'block';
    else error.style.removeProperty('display');
    confirm.disabled = Boolean(message);
  }

  function beginDescriptionEdit(input) {
    if (!input || input.dataset.submitting === 'true') return;
    var wrapper = input.closest('.time-entry-description-wrapper');
    if (input.readOnly) {
      input.dataset.originalValue = input.value;
      input.readOnly = false;
    }
    if (wrapper) wrapper.classList.add('is-editing');
    input.focus();
    window.requestAnimationFrame(function () {
      input.setSelectionRange(input.value.length, input.value.length);
    });
  }

  function cancelDescriptionEdit(input) {
    if (!input || input.dataset.submitting === 'true') return;
    if (input.dataset.originalValue !== undefined) {
      input.value = input.dataset.originalValue;
    }
    input.readOnly = true;
    var wrapper = input.closest('.time-entry-description-wrapper');
    if (wrapper) wrapper.classList.remove('is-editing');
  }

  function submitDescriptionEdit(input) {
    if (!input || input.readOnly || input.dataset.submitting === 'true') return;
    input.dataset.submitting = 'true';
    if (document.activeElement === input) input.blur();
    htmx.trigger(input, 'description-save');
  }

  document.addEventListener(
    'focus',
    function (evt) {
      if (evt.target.matches && evt.target.matches('.time-entry-description-editor')) {
        beginDescriptionEdit(evt.target);
      }
    },
    true
  );

  document.addEventListener('keydown', function (evt) {
    var input = evt.target;
    if (!input.matches) return;

    if (input.matches('.time-entry-project-search') && evt.key === 'ArrowDown') {
      var firstProject = input
        .closest('.time-entry-project-dialog')
        .querySelector('.time-entry-project-option:not(:disabled)');
      if (firstProject) {
        evt.preventDefault();
        firstProject.focus();
      }
      return;
    }

    if (
      input.matches('.time-entry-project-option') &&
      (evt.key === 'ArrowDown' || evt.key === 'ArrowUp')
    ) {
      var projectDialog = input.closest('.time-entry-project-dialog');
      var projectOptions = Array.from(
        projectDialog.querySelectorAll('.time-entry-project-option:not(:disabled)')
      );
      var projectIndex = projectOptions.indexOf(input);
      var nextProjectIndex = projectIndex + (evt.key === 'ArrowDown' ? 1 : -1);
      evt.preventDefault();
      if (nextProjectIndex >= 0 && nextProjectIndex < projectOptions.length) {
        projectOptions[nextProjectIndex].focus();
      } else if (nextProjectIndex < 0) {
        projectDialog.querySelector('.time-entry-project-search').focus();
      }
      return;
    }

    if (!input.matches('.time-entry-description-editor')) return;

    if (evt.key === 'Escape') {
      evt.preventDefault();
      cancelDescriptionEdit(input);
      input.blur();
      return;
    }

    if ((evt.key === 'Enter' || evt.key === 'F2') && input.readOnly) {
      evt.preventDefault();
      beginDescriptionEdit(input);
      return;
    }

    if (evt.key === 'Enter') {
      evt.preventDefault();
      submitDescriptionEdit(input);
    }
  });

  document.addEventListener(
    'blur',
    function (evt) {
      if (evt.target.matches && evt.target.matches('.time-entry-description-editor')) {
        submitDescriptionEdit(evt.target);
      }
    },
    true
  );

  document.body.addEventListener('htmx:before:request', function (evt) {
    var ctx = evt.detail && evt.detail.ctx;
    var input = ctx && ctx.sourceElement;
    var startForm =
      input && input.matches && input.matches('.running-timer-start-form')
        ? input
        : input && input.closest && input.closest('.running-timer-start-form');
    if (startForm) restoreStartEditorFocusAfterSwap = true;
    if (!input || !input.matches('.time-entry-description-editor')) return;
    input.readOnly = true;
    var wrapper = input.closest('.time-entry-description-wrapper');
    if (wrapper) wrapper.classList.add('is-saving');
  });

  document.body.addEventListener('htmx:after:request', function (evt) {
    var ctx = evt.detail && evt.detail.ctx;
    var input = ctx && ctx.sourceElement;
    var successful = ctx && ctx.response && ctx.response.status < 400;
    if (!input || !input.matches) return;
    if (
      !successful &&
      input.matches('.time-entry-split-confirm, .time-entry-split-form')
    ) {
      var splitDialog = input.closest('.time-entry-split-dialog');
      var splitError = splitDialog.querySelector('.time-entry-split-error');
      if (!splitError) {
        splitError = document.createElement('div');
        splitError.className = 'time-entry-split-error';
        splitError.setAttribute('role', 'alert');
        splitDialog
          .querySelector('.time-entry-split-dialog-body')
          .insertAdjacentElement('afterbegin', splitError);
      }
      splitError.textContent = 'Could not split this time entry. Try again.';
      return;
    }
    if (!successful && input.matches('.time-entry-delete-confirm')) {
      var deleteDialog = input.closest('.time-entry-delete-dialog');
      var deleteError = deleteDialog.querySelector('.time-entry-delete-error');
      if (!deleteError) {
        deleteError = document.createElement('div');
        deleteError.className = 'time-entry-delete-error';
        deleteError.setAttribute('role', 'alert');
        deleteDialog
          .querySelector('.time-entry-delete-dialog-body')
          .insertAdjacentElement('afterbegin', deleteError);
      }
      deleteError.textContent = 'Could not delete this time entry. Try again.';
      return;
    }
    if (
      !successful &&
      input.matches('.time-entry-project-option')
    ) {
      var projectDialog = input.closest('.time-entry-project-dialog');
      var projectAlert = projectDialog.querySelector('.time-entry-project-alert');
      if (!projectAlert) {
        projectAlert = document.createElement('div');
        projectAlert.className = 'time-entry-project-alert';
        projectAlert.setAttribute('role', 'status');
        projectDialog
          .querySelector('.time-entry-project-dialog-header')
          .insertAdjacentElement('afterend', projectAlert);
      }
      projectAlert.textContent = 'Could not save the project. Choose a project to retry.';
      return;
    }
    if (!input.matches('.time-entry-description-editor')) return;
    if (successful) return;
    input.dataset.submitting = 'false';
    input.readOnly = false;
    var wrapper = input.closest('.time-entry-description-wrapper');
    if (wrapper) {
      wrapper.classList.remove('is-saving');
      var error = wrapper.querySelector('.time-entry-description-error');
      if (!error) {
        error = document.createElement('span');
        error.className = 'time-entry-description-error';
        error.setAttribute('role', 'status');
        wrapper.appendChild(error);
      }
      error.textContent = 'Could not save. Press Enter to retry.';
    }
    input.focus();
  });

  document.body.addEventListener('htmx:before:swap', function (evt) {
    var ctx = evt.detail && evt.detail.ctx;
    var target = ctx && ctx.target;
    if (typeof target === 'string') target = document.querySelector(target);
    disposeStartEditors(target);
  });

  document.body.addEventListener('htmx:after:swap', function (evt) {
    var ctx = evt.detail && evt.detail.ctx;
    var tgt = ctx && ctx.target;
    if (typeof tgt === 'string') tgt = document.querySelector(tgt);
    // HTMX keeps the original target in ctx after an outerHTML replacement.
    if (tgt && !tgt.isConnected && tgt.id) {
      tgt = document.getElementById(tgt.id);
    }
    startElapsedTimers();
    initializeStartEditors(tgt || document);
    if (tgt && tgt.id === 'result' && restoreStartEditorFocusAfterSwap) {
      var updatedStartTrigger = document.querySelector('.running-timer-elapsed-trigger');
      if (updatedStartTrigger) updatedStartTrigger.focus();
      restoreStartEditorFocusAfterSwap = false;
    } else if (
      tgt &&
      tgt.matches &&
      tgt.matches('.running-timer-start-dialog')
    ) {
      restoreStartEditorFocusAfterSwap = false;
    }
    if (tgt && tgt.id === 'client-select-wrapper') {
      restoreClientSelection();
    }
    var deleteDialog =
      tgt && tgt.querySelector && tgt.querySelector('.time-entry-delete-dialog');
    if (
      deleteDialog &&
      deleteDialog.getAttribute('data-open') === 'true' &&
      !deleteDialog.open
    ) {
      deleteDialog.showModal();
    }
    var splitDialog =
      tgt && tgt.querySelector && tgt.querySelector('.time-entry-split-dialog');
    if (
      splitDialog &&
      splitDialog.getAttribute('data-open') === 'true' &&
      !splitDialog.open
    ) {
      initializeSplitDialog(splitDialog);
      splitDialog.showModal();
    }
    var descriptionInput =
      tgt && tgt.querySelector && tgt.querySelector('.time-entry-description-editor');
    if (
      descriptionInput &&
      descriptionInput.getAttribute('data-editing') === 'true'
    ) {
      beginDescriptionEdit(descriptionInput);
    }
    var projectPicker =
      tgt && tgt.matches && tgt.matches('.time-entry-project-picker')
        ? tgt
        : tgt &&
          tgt.querySelector &&
          tgt.querySelector('.time-entry-project-picker');
    if (projectPicker) {
      var projectRow = projectPicker.closest('.time-entry-row, .running-timer-toolbar');
      var projectColor = projectPicker.getAttribute('data-project-color');
      if (projectRow) {
        if (projectColor) {
          projectRow.style.setProperty('--project-color', projectColor);
        } else {
          projectRow.style.removeProperty('--project-color');
        }
      }
      if (projectPicker.getAttribute('data-open') === 'true') {
        openProjectPicker(projectPicker.querySelector('.time-entry-project-trigger'));
      }
    }
  });
})();
