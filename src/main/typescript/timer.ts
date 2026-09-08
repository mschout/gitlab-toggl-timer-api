const STORAGE_KEY = 'gitlabTogglTimer.timer.selectedClient';
const ISSUE_URL_KEY = 'gitlabTogglTimer.timer.issueUrl';
let restoreStartEditorFocusAfterSwap = false;

function getWorkspaceId() {
  const el = document.querySelector<HTMLInputElement | HTMLSelectElement>(
    '#workspaceId',
  );
  return el && el.value ? el.value : null;
}

function readStored() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    const stored: unknown = raw ? JSON.parse(raw) : null;
    if (
      typeof stored !== 'object' ||
      stored === null ||
      !('workspaceId' in stored) ||
      typeof stored.workspaceId !== 'string' ||
      !('clientId' in stored) ||
      typeof stored.clientId !== 'string'
    )
      return null;
    return { workspaceId: stored.workspaceId, clientId: stored.clientId };
  } catch (e) {
    return null;
  }
}

function writeStored(workspaceId: string, clientId: string) {
  try {
    localStorage.setItem(
      STORAGE_KEY,
      JSON.stringify({
        workspaceId: String(workspaceId),
        clientId: String(clientId),
      }),
    );
  } catch (e) {}
}

function clearStored() {
  try {
    localStorage.removeItem(STORAGE_KEY);
  } catch (e) {}
}

function restoreClientSelection() {
  const clientEl = document.querySelector<HTMLInputElement | HTMLSelectElement>(
    '#clientId',
  );
  if (!clientEl) return;
  if (clientEl.value) return;
  const workspaceId = getWorkspaceId();
  if (!workspaceId) return;
  const stored = readStored();
  if (!stored || stored.workspaceId !== String(workspaceId)) return;
  const clientId = String(stored.clientId);
  if (clientEl instanceof HTMLSelectElement) {
    for (let i = 0; i < clientEl.options.length; i++) {
      if (clientEl.options[i]?.value === clientId) {
        clientEl.value = clientId;
        return;
      }
    }
  } else {
    clientEl.value = clientId;
  }
}

function restoreIssueUrl() {
  const el = document.querySelector<HTMLInputElement>('#issueUrl');
  if (!el || el.value) return;
  try {
    const stored = localStorage.getItem(ISSUE_URL_KEY);
    if (stored) el.value = stored;
  } catch (e) {}
}

function clearIssueUrl() {
  const input = document.querySelector<HTMLInputElement>('#issueUrl');
  if (input) input.value = '';
  try {
    localStorage.removeItem(ISSUE_URL_KEY);
  } catch (e) {}
}

function padElapsedPart(value: number) {
  return value < 10 ? '0' + value : '' + value;
}

const basePageTitle = document.title;

function elapsedSeconds(timer: Element | null | undefined) {
  if (!timer) return null;
  const startedAt = new Date(timer.getAttribute('data-started-at') ?? '');
  if (isNaN(startedAt.getTime())) return null;
  return Math.max(0, Math.floor((Date.now() - startedAt.getTime()) / 1000));
}

function formatElapsedTitle(seconds: number) {
  const hours = Math.floor(seconds / 3600);
  const minutes = Math.floor((seconds % 3600) / 60);
  const remainingSeconds = seconds % 60;
  if (hours === 0) return minutes + ':' + padElapsedPart(remainingSeconds);
  return (
    hours +
    ':' +
    padElapsedPart(minutes) +
    ':' +
    padElapsedPart(remainingSeconds)
  );
}

function renderPageTitle(runningTimer: Element | null | undefined) {
  const seconds = elapsedSeconds(runningTimer);
  document.title =
    seconds === null
      ? basePageTitle
      : formatElapsedTitle(seconds) + ' • ' + basePageTitle;
}

function renderElapsedTime(el: Element) {
  const seconds = elapsedSeconds(el);
  if (seconds === null) return;
  const hours = Math.floor(seconds / 3600);
  const minutes = Math.floor((seconds % 3600) / 60);
  const remainingSeconds = seconds % 60;
  el.textContent =
    padElapsedPart(hours) +
    ':' +
    padElapsedPart(minutes) +
    ':' +
    padElapsedPart(remainingSeconds);
}

function updateRunningSplitEligibility() {
  document
    .querySelectorAll<HTMLButtonElement>(
      '.running-timer-split-trigger[data-enable-at]',
    )
    .forEach(function (trigger) {
      const enableAt = Number(trigger.getAttribute('data-enable-at'));
      if (!Number.isFinite(enableAt) || Date.now() < enableAt) return;
      trigger.disabled = false;
      trigger.removeAttribute('aria-describedby');
      trigger.removeAttribute('data-enable-at');
    });
}

function formatTotalTime(seconds: number) {
  const hours = Math.floor(seconds / 3600);
  const minutes = Math.floor((seconds % 3600) / 60);
  const remainingSeconds = seconds % 60;
  return (
    hours +
    ':' +
    padElapsedPart(minutes) +
    ':' +
    padElapsedPart(remainingSeconds)
  );
}

function renderTimeTotals() {
  const runningTimer = document.querySelector<HTMLElement>('.js-elapsed-time');
  const startedAt =
    runningTimer &&
    new Date(runningTimer.getAttribute('data-started-at') ?? '');
  const startedAtMs = startedAt && startedAt.getTime();
  const elapsedSeconds =
    startedAtMs && !isNaN(startedAtMs)
      ? Math.max(0, Math.floor((Date.now() - startedAtMs) / 1000))
      : 0;

  document
    .querySelectorAll<HTMLElement>('.js-time-total')
    .forEach(function (total) {
      let seconds = Number(total.getAttribute('data-base-seconds')) || 0;
      const periodStart = new Date(
        total.getAttribute('data-period-start') ?? '',
      ).getTime();
      const periodEnd = new Date(
        total.getAttribute('data-period-end') ?? '',
      ).getTime();
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
    delete window.__togglElapsedIntervalId;
  }
  const timers = document.querySelectorAll<HTMLElement>('.js-elapsed-time');
  timers.forEach(renderElapsedTime);
  updateRunningSplitEligibility();
  renderTimeTotals();
  renderPageTitle(timers[0]);
  if (!timers.length) return;
  window.__togglElapsedIntervalId = setInterval(function () {
    const currentTimers =
      document.querySelectorAll<HTMLElement>('.js-elapsed-time');
    if (!currentTimers.length) {
      clearInterval(window.__togglElapsedIntervalId);
      delete window.__togglElapsedIntervalId;
      renderPageTitle(null);
      return;
    }
    currentTimers.forEach(renderElapsedTime);
    updateRunningSplitEligibility();
    renderTimeTotals();
    renderPageTitle(currentTimers[0]);
  }, 1000);
}

function parseLocalDate(value: string | undefined) {
  const parts = value && value.split('-').map(Number);
  if (!parts || parts.length !== 3 || parts.some(Number.isNaN)) return null;
  const [year, month, day] = parts;
  if (year === undefined || month === undefined || day === undefined)
    return null;
  return new Date(year, month - 1, day);
}

function formatLocalDate(date: Date) {
  return (
    date.getFullYear() +
    '-' +
    String(date.getMonth() + 1).padStart(2, '0') +
    '-' +
    String(date.getDate()).padStart(2, '0')
  );
}

function formatStartDate(date: Date, today: string | undefined) {
  const isoDate = formatLocalDate(date);
  if (isoDate === today) return 'Today';
  return (
    String(date.getMonth() + 1).padStart(2, '0') +
    '/' +
    String(date.getDate()).padStart(2, '0')
  );
}

function startEditorDialogs(root: Document | Element | null | undefined) {
  if (!root) return [];
  const dialogs: RunningTimerStartDialog[] = [];
  if (
    root instanceof HTMLDialogElement &&
    root.matches('.running-timer-start-dialog')
  )
    dialogs.push(root);
  root
    .querySelectorAll<RunningTimerStartDialog>('.running-timer-start-dialog')
    .forEach(function (dialog) {
      dialogs.push(dialog);
    });
  return dialogs;
}

function initializeStartEditor(dialog: RunningTimerStartDialog | null) {
  if (!dialog || dialog._runningTimerDatepicker || !window.AirDatepicker)
    return;
  const calendar = dialog.querySelector<HTMLElement>(
    '.running-timer-start-calendar',
  );
  const dateDisplay = dialog.querySelector<HTMLInputElement>(
    '.running-timer-start-date-display',
  );
  const dateInput = dialog.querySelector<HTMLInputElement>(
    '.running-timer-start-date',
  );
  const selectedDate = parseLocalDate(dateInput?.value);
  const today = parseLocalDate(dialog.dataset.today);
  if (!calendar || !dateDisplay || !dateInput || !selectedDate || !today)
    return;

  dialog._runningTimerDatepicker = new window.AirDatepicker(calendar, {
    inline: true,
    locale: {
      days: [
        'Sunday',
        'Monday',
        'Tuesday',
        'Wednesday',
        'Thursday',
        'Friday',
        'Saturday',
      ],
      daysShort: ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'],
      daysMin: ['Su', 'Mo', 'Tu', 'We', 'Th', 'Fr', 'Sa'],
      months: [
        'January',
        'February',
        'March',
        'April',
        'May',
        'June',
        'July',
        'August',
        'September',
        'October',
        'November',
        'December',
      ],
      monthsShort: [
        'Jan',
        'Feb',
        'Mar',
        'Apr',
        'May',
        'Jun',
        'Jul',
        'Aug',
        'Sep',
        'Oct',
        'Nov',
        'Dec',
      ],
      today: 'Today',
      clear: 'Clear',
      dateFormat: 'MM/dd/yyyy',
      timeFormat: 'hh:mm aa',
      firstDay: 1,
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
      years: 'yyyy1 - yyyy2',
    },
    onRenderCell: function (cell) {
      if (cell.cellType !== 'day') return;
      if (formatLocalDate(cell.date) === dialog.dataset.today) {
        return { classes: 'running-timer-start-today' };
      }
    },
    onSelect: function (selection) {
      if (!(selection.date instanceof Date)) return;
      dateInput.value = formatLocalDate(selection.date);
      dateDisplay.value = formatStartDate(selection.date, dialog.dataset.today);
    },
  });
}

function initializeStartEditors(root: Document | Element | null | undefined) {
  startEditorDialogs(root).forEach(function (dialog) {
    initializeStartEditor(dialog);
    if (dialog.dataset.open === 'true' && !dialog.open) {
      dialog.showModal();
      const timeInput = dialog.querySelector<HTMLInputElement>(
        '.running-timer-start-time',
      );
      if (timeInput) {
        timeInput.focus();
        timeInput.select();
      }
    }
  });
}

function disposeStartEditors(root: Document | Element | null | undefined) {
  startEditorDialogs(root).forEach(function (dialog) {
    if (!dialog._runningTimerDatepicker) return;
    dialog._runningTimerDatepicker.destroy();
    delete dialog._runningTimerDatepicker;
  });
}

function resetStartEditor(dialog: RunningTimerStartDialog | null) {
  if (!dialog) return;
  const dateInput = dialog.querySelector<HTMLInputElement>(
    '.running-timer-start-date',
  );
  const dateDisplay = dialog.querySelector<HTMLInputElement>(
    '.running-timer-start-date-display',
  );
  const timeInput = dialog.querySelector<HTMLInputElement>(
    '.running-timer-start-time',
  );
  const originalDate = parseLocalDate(dialog.dataset.initialStartDate);
  if (dateInput) dateInput.value = dialog.dataset.initialStartDate ?? '';
  if (dateDisplay && originalDate) {
    dateDisplay.value = formatStartDate(originalDate, dialog.dataset.today);
  }
  if (timeInput) timeInput.value = dialog.dataset.initialStartTime ?? '';
  if (dialog._runningTimerDatepicker && originalDate) {
    dialog._runningTimerDatepicker.selectDate(originalDate, { silent: true });
    dialog._runningTimerDatepicker.setViewDate(originalDate);
  }
}

function closeStartEditor(dialog: RunningTimerStartDialog | null) {
  if (!dialog) return;
  resetStartEditor(dialog);
  dialog.close();
  const trigger = document.querySelector<HTMLButtonElement>(
    '.running-timer-elapsed-trigger[aria-controls="' + dialog.id + '"]',
  );
  if (trigger) trigger.focus();
}

function openStartEditor(trigger: HTMLElement | null) {
  if (!trigger) return;
  const dialogId = trigger.getAttribute('aria-controls');
  const dialog = dialogId ? document.getElementById(dialogId) : null;
  if (!(dialog instanceof HTMLDialogElement)) return;
  resetStartEditor(dialog);
  initializeStartEditor(dialog);
  if (!dialog.open) dialog.showModal();
  const timeInput = dialog.querySelector<HTMLInputElement>(
    '.running-timer-start-time',
  );
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
  document
    .querySelectorAll<HTMLDialogElement>('.time-entry-split-dialog')
    .forEach(initializeSplitDialog);
});

document.addEventListener('cancel', function (evt) {
  if (!(evt.target instanceof HTMLDialogElement)) return;
  if (!evt.target.matches || !evt.target.matches('.running-timer-start-dialog'))
    return;
  evt.preventDefault();
  closeStartEditor(evt.target);
});

document.addEventListener('submit', function (evt) {
  if (!(evt.target instanceof HTMLFormElement)) return;
  if (evt.target.matches && evt.target.matches('.running-timer-start-form')) {
    restoreStartEditorFocusAfterSwap = true;
  }
});

document.body.addEventListener('issueUrlConsumed', clearIssueUrl);

document.addEventListener('input', function (evt) {
  const target = evt.target;
  if (!(target instanceof HTMLInputElement)) return;
  if (target.matches('.time-entry-split-slider')) {
    renderSplitDialog(
      target.closest<HTMLDialogElement>('.time-entry-split-dialog'),
      false,
    );
    return;
  }
  if (target.matches('.time-entry-split-manual-input')) {
    applyManualSplit(
      target.closest<HTMLDialogElement>('.time-entry-split-dialog'),
    );
    return;
  }
  if (target.matches('.stopped-timer-project-search')) {
    filterStoppedTimerProjects(target);
  }
});

document.addEventListener(
  'toggle',
  function (evt) {
    if (
      !(evt.target instanceof HTMLDetailsElement) ||
      !evt.target.matches('.time-entry-split-manual')
    )
      return;
    const dialog = evt.target.closest<HTMLDialogElement>(
      '.time-entry-split-dialog',
    );
    if (!dialog) return;
    if (evt.target.open) {
      renderSplitDialog(dialog, false);
      const input = dialog.querySelector<HTMLInputElement>(
        '.time-entry-split-manual-input',
      );
      if (!input) return;
      if (input) input.focus();
    } else {
      setManualSplitError(dialog, '');
      renderSplitDialog(dialog, false);
    }
  },
  true,
);

document.addEventListener('change', function (evt) {
  const target = evt.target;
  if (
    !(target instanceof HTMLInputElement || target instanceof HTMLSelectElement)
  )
    return;
  if (target.matches && target.matches('.time-entry-split-mode-input')) {
    renderSplitDialog(
      target.closest<HTMLDialogElement>('.time-entry-split-dialog'),
      false,
    );
    return;
  }
  if (!target.id) return;
  if (target.id === 'clientId') {
    const workspaceId = getWorkspaceId();
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
  if (!(evt.target instanceof Element)) return;
  const restartTrigger =
    evt.target.closest &&
    evt.target.closest<HTMLButtonElement>('.time-entry-restart-trigger');
  if (restartTrigger) {
    evt.preventDefault();
    requestEntryRestart(restartTrigger);
    return;
  }

  const startTrigger =
    evt.target.closest &&
    evt.target.closest<HTMLButtonElement>('.running-timer-elapsed-trigger');
  if (startTrigger) {
    openStartEditor(startTrigger);
    return;
  }

  const startCancel =
    evt.target.closest &&
    evt.target.closest<HTMLButtonElement>('.running-timer-start-cancel');
  if (startCancel) {
    closeStartEditor(
      startCancel.closest<RunningTimerStartDialog>(
        '.running-timer-start-dialog',
      ),
    );
    return;
  }

  if (
    evt.target instanceof HTMLDialogElement &&
    evt.target.matches('.running-timer-start-dialog')
  ) {
    const startDialogRect = evt.target.getBoundingClientRect();
    const outsideStartDialog =
      evt.clientX < startDialogRect.left ||
      evt.clientX > startDialogRect.right ||
      evt.clientY < startDialogRect.top ||
      evt.clientY > startDialogRect.bottom;
    if (outsideStartDialog) closeStartEditor(evt.target);
    return;
  }

  const copyDescription =
    evt.target.closest &&
    evt.target.closest<HTMLButtonElement>('.time-entry-copy-description');
  if (copyDescription) {
    copyEntryDescription(copyDescription);
    return;
  }

  const splitTrigger =
    evt.target.closest &&
    evt.target.closest<HTMLButtonElement>('.time-entry-split-trigger');
  if (splitTrigger) {
    openSplitDialog(splitTrigger);
    return;
  }

  const splitClose =
    evt.target.closest &&
    evt.target.closest<HTMLButtonElement>('.time-entry-split-close');
  if (splitClose) {
    const splitDialog = splitClose.closest<HTMLDialogElement>(
      '.time-entry-split-dialog',
    );
    if (splitDialog) splitDialog.close();
    return;
  }

  if (
    evt.target instanceof HTMLDialogElement &&
    evt.target.matches('.time-entry-split-dialog')
  ) {
    const splitDialogRect = evt.target.getBoundingClientRect();
    const outsideSplitDialog =
      evt.clientX < splitDialogRect.left ||
      evt.clientX > splitDialogRect.right ||
      evt.clientY < splitDialogRect.top ||
      evt.clientY > splitDialogRect.bottom;
    if (outsideSplitDialog) evt.target.close();
    return;
  }

  const deleteTrigger =
    evt.target.closest &&
    evt.target.closest<HTMLButtonElement>('.time-entry-delete-trigger');
  if (deleteTrigger) {
    openDeleteDialog(deleteTrigger);
    return;
  }

  const deleteClose =
    evt.target.closest &&
    evt.target.closest<HTMLButtonElement>('.time-entry-delete-close');
  if (deleteClose) {
    const deleteDialog = deleteClose.closest<HTMLDialogElement>(
      '.time-entry-delete-dialog',
    );
    if (deleteDialog) deleteDialog.close();
    return;
  }

  if (
    evt.target instanceof HTMLDialogElement &&
    evt.target.matches('.time-entry-delete-dialog')
  ) {
    const deleteDialogRect = evt.target.getBoundingClientRect();
    const outsideDeleteDialog =
      evt.clientX < deleteDialogRect.left ||
      evt.clientX > deleteDialogRect.right ||
      evt.clientY < deleteDialogRect.top ||
      evt.clientY > deleteDialogRect.bottom;
    if (outsideDeleteDialog) evt.target.close();
    return;
  }

  const stoppedProjectOption =
    evt.target.closest &&
    evt.target.closest<HTMLButtonElement>('.stopped-timer-project-option');
  if (stoppedProjectOption) {
    selectStoppedTimerProject(stoppedProjectOption);
    return;
  }

  const projectTrigger =
    evt.target.closest &&
    evt.target.closest<HTMLButtonElement>('.time-entry-project-trigger');
  if (projectTrigger) {
    openProjectPicker(projectTrigger);
    return;
  }

  const projectClose =
    evt.target.closest &&
    evt.target.closest<HTMLButtonElement>('.time-entry-project-close');
  if (projectClose) {
    const closeDialog = projectClose.closest<HTMLDialogElement>(
      '.time-entry-project-dialog',
    );
    if (closeDialog) closeDialog.close();
    return;
  }

  if (
    evt.target instanceof HTMLDialogElement &&
    evt.target.matches('.time-entry-project-dialog')
  ) {
    const dialogRect = evt.target.getBoundingClientRect();
    const outsideDialog =
      evt.clientX < dialogRect.left ||
      evt.clientX > dialogRect.right ||
      evt.clientY < dialogRect.top ||
      evt.clientY > dialogRect.bottom;
    if (outsideDialog) evt.target.close();
    return;
  }

  const descriptionInput =
    evt.target.closest &&
    evt.target.closest<HTMLInputElement>('.time-entry-description-editor');
  if (descriptionInput) {
    beginDescriptionEdit(descriptionInput);
    return;
  }

  const btn =
    evt.target.closest && evt.target.closest<HTMLElement>('#issueUrlClear');
  if (!btn) return;
  clearIssueUrl();
  const input = document.querySelector<HTMLInputElement>('#issueUrl');
  if (!input) return;
  input.focus();
});

function openProjectPicker(trigger: HTMLElement | null) {
  if (!trigger) return;
  const dialogId = trigger.getAttribute('aria-controls');
  const dialog = dialogId ? document.getElementById(dialogId) : null;
  if (!(dialog instanceof HTMLDialogElement)) return;
  if (!dialog.open) dialog.showModal();
  const search = dialog.querySelector<HTMLInputElement>(
    '.time-entry-project-search',
  );
  if (!search) return;
  search.focus();
  window.htmx.trigger(search, 'project-search');
}

function filterStoppedTimerProjects(search: HTMLInputElement) {
  const dialog = search.closest<HTMLDialogElement>(
    '.stopped-timer-project-dialog',
  );
  if (!dialog) return;
  const query = search.value.trim().toLowerCase();
  let visibleCount = 0;
  dialog
    .querySelectorAll<HTMLButtonElement>('.stopped-timer-project-option')
    .forEach(function (option) {
      const searchableText = [
        option.getAttribute('data-project-name'),
        option.getAttribute('data-project-client'),
      ]
        .filter(Boolean)
        .join(' ')
        .toLowerCase();
      option.hidden = query !== '' && !searchableText.includes(query);
      if (!option.hidden) visibleCount += 1;
    });
  const empty = dialog.querySelector<HTMLElement>(
    '.stopped-timer-project-results-empty',
  );
  if (empty) {
    empty.classList.toggle('d-none', visibleCount !== 0);
    empty.textContent = query
      ? 'No matching projects.'
      : 'No active projects in this workspace.';
  }
}

function selectStoppedTimerProject(option: HTMLButtonElement) {
  const picker = option.closest<HTMLElement>('.stopped-timer-project-picker');
  if (!picker) return;
  const projectId = option.getAttribute('data-project-id') || '';
  const projectName = option.getAttribute('data-project-name') || '';
  const projectClient = option.getAttribute('data-project-client') || '';
  const projectColor = option.getAttribute('data-project-color') || '';
  const projectInput = picker.querySelector<HTMLInputElement>(
    'input[name="projectId"]',
  );
  const projectNameLabel = picker.querySelector<HTMLElement>(
    '.stopped-timer-project-name',
  );
  const projectClientLabel = picker.querySelector<HTMLElement>(
    '.stopped-timer-project-client',
  );
  const projectClientSeparator = picker.querySelector<HTMLElement>(
    '.stopped-timer-project-client-separator',
  );
  const toolbar = picker.closest<HTMLElement>('.running-timer-toolbar');

  if (projectInput) {
    projectInput.value = projectId;
    projectInput.setAttribute('value', projectId);
  }
  if (projectNameLabel) {
    projectNameLabel.textContent = projectName;
    projectNameLabel.classList.remove('fst-italic');
  }
  if (projectClientLabel) {
    projectClientLabel.textContent = projectClient;
    projectClientLabel.classList.toggle('d-none', !projectClient);
  }
  if (projectClientSeparator) {
    projectClientSeparator.classList.toggle('d-none', !projectClient);
  }
  picker.setAttribute('data-project-color', projectColor);
  if (toolbar && projectColor)
    toolbar.style.setProperty('--project-color', projectColor);

  picker
    .querySelectorAll<HTMLButtonElement>('.stopped-timer-project-option')
    .forEach(function (candidate) {
      const selected = candidate === option;
      candidate.classList.toggle('is-selected', selected);
      candidate.disabled = selected;
    });

  const dialog = picker.querySelector<HTMLDialogElement>(
    '.stopped-timer-project-dialog',
  );
  if (dialog) dialog.close();
  const trigger = picker.querySelector<HTMLButtonElement>(
    '.time-entry-project-trigger',
  );
  if (trigger) trigger.focus();
}

function copyEntryDescription(button: HTMLButtonElement | null) {
  if (!button || button.disabled) return;
  const label = button.querySelector<HTMLElement>('.time-entry-copy-label');
  const description = button.getAttribute('data-description') || '';
  const clipboard = navigator.clipboard;
  const copyRequest =
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

function hideEntryActionsMenu(element: HTMLElement | null) {
  const actions =
    element && element.closest<HTMLElement>('.time-entry-actions');
  const trigger =
    actions &&
    actions.querySelector<HTMLButtonElement>('.time-entry-actions-trigger');
  if (trigger && window.bootstrap) {
    window.bootstrap.Dropdown.getOrCreateInstance(trigger).hide();
  }
}

function openDeleteDialog(trigger: HTMLElement | null) {
  if (!trigger) return;
  const dialogId = trigger.getAttribute('aria-controls');
  const dialog = dialogId ? document.getElementById(dialogId) : null;
  if (!(dialog instanceof HTMLDialogElement)) return;
  hideEntryActionsMenu(trigger);
  if (!dialog.open) dialog.showModal();
}

function openSplitDialog(trigger: HTMLElement | null) {
  if (!trigger) return;
  const dialogId = trigger.getAttribute('aria-controls');
  const dialog = dialogId ? document.getElementById(dialogId) : null;
  if (!(dialog instanceof HTMLDialogElement)) return;
  hideEntryActionsMenu(trigger);
  initializeSplitDialog(dialog);
  if (!dialog.open) dialog.showModal();
}

function initializeSplitDialog(dialog: HTMLDialogElement | null) {
  if (!dialog) return;
  renderSplitDialog(dialog, false);
}

function splitMode(dialog: HTMLDialogElement) {
  const selected = dialog.querySelector<HTMLInputElement>(
    '.time-entry-split-mode-input:checked',
  );
  return selected ? selected.value : 'elapsed';
}

function formatSplitDuration(seconds: number) {
  const safeSeconds = Math.max(0, Math.floor(seconds));
  const hours = Math.floor(safeSeconds / 3600);
  const minutes = Math.floor((safeSeconds % 3600) / 60);
  const remainingSeconds = safeSeconds % 60;
  return (
    String(hours).padStart(2, '0') +
    ':' +
    String(minutes).padStart(2, '0') +
    ':' +
    String(remainingSeconds).padStart(2, '0')
  );
}

function formatSplitClock(
  dialog: HTMLDialogElement,
  offset: number,
  twentyFourHour: boolean,
) {
  const startMilliseconds = Number(dialog.dataset.startEpochMilliseconds);
  const instant = new Date(startMilliseconds + offset * 1000);
  const options: Intl.DateTimeFormatOptions = {
    timeZone: dialog.dataset.timeZone,
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  };
  if (twentyFourHour) {
    options.hourCycle = 'h23';
  } else {
    options.hour = 'numeric';
  }
  return new Intl.DateTimeFormat('en-US', options).format(instant);
}

function renderSplitDialog(
  dialog: HTMLDialogElement | null,
  preserveManualValue: boolean,
) {
  if (!dialog) return;
  const slider = dialog.querySelector<HTMLInputElement>(
    '.time-entry-split-slider',
  );
  if (!slider) return;
  const offset = Number(slider.value);
  const duration = Number(dialog.dataset.durationSeconds);
  const clock = dialog.querySelector<HTMLElement>('.time-entry-split-clock');
  const firstDuration = dialog.querySelector<HTMLElement>(
    '.time-entry-split-first-duration',
  );
  const secondDuration = dialog.querySelector<HTMLElement>(
    '.time-entry-split-second-duration',
  );
  if (clock) clock.textContent = formatSplitClock(dialog, offset, false);
  if (firstDuration) firstDuration.textContent = formatSplitDuration(offset);
  if (secondDuration)
    secondDuration.textContent = formatSplitDuration(duration - offset);

  const manual = dialog.querySelector<HTMLDetailsElement>(
    '.time-entry-split-manual',
  );
  if (!manual || !manual.open || preserveManualValue) return;
  const input = dialog.querySelector<HTMLInputElement>(
    '.time-entry-split-manual-input',
  );
  if (!input) return;
  input.value =
    splitMode(dialog) === 'elapsed'
      ? formatSplitDuration(offset)
      : formatSplitClock(dialog, offset, true);
  const help = dialog.querySelector<HTMLElement>(
    '.time-entry-split-manual-help',
  );
  if (!help) return;
  help.textContent =
    splitMode(dialog) === 'elapsed'
      ? 'Enter elapsed time after the start.'
      : 'Enter a 24-hour clock time in ' + dialog.dataset.timeZone + '.';
  setManualSplitError(dialog, '');
}

function parseElapsedSplit(value: string) {
  const match = /^(\d{2,}):([0-5]\d):([0-5]\d)$/.exec(value.trim());
  if (!match) return null;
  return Number(match[1]) * 3600 + Number(match[2]) * 60 + Number(match[3]);
}

function parseClockSplit(value: string) {
  const match = /^([01]\d|2[0-3]):([0-5]\d):([0-5]\d)$/.exec(value.trim());
  if (!match) return null;
  return Number(match[1]) * 3600 + Number(match[2]) * 60 + Number(match[3]);
}

function applyManualSplit(dialog: HTMLDialogElement | null) {
  if (!dialog) return;
  const input = dialog.querySelector<HTMLInputElement>(
    '.time-entry-split-manual-input',
  );
  if (!input) return;
  const mode = splitMode(dialog);
  const duration = Number(dialog.dataset.durationSeconds);
  let offset: number | null;
  if (mode === 'elapsed') {
    offset = parseElapsedSplit(input.value);
    if (offset === null) {
      setManualSplitError(dialog, 'Use HH:MM:SS, for example 00:29:00.');
      return;
    }
  } else {
    const clockSeconds = parseClockSplit(input.value);
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
        'This clock time may occur more than once. Use Elapsed or the slider.',
      );
      return;
    }
    offset = clockSeconds - Number(dialog.dataset.startLocalSecondOfDay);
    if (offset < 0) offset += 86400;
  }

  if (offset < 1 || offset >= duration) {
    setManualSplitError(
      dialog,
      'Choose a time that leaves at least one second on each side.',
    );
    return;
  }
  const slider = dialog.querySelector<HTMLInputElement>(
    '.time-entry-split-slider',
  );
  if (!slider) return;
  slider.value = String(offset);
  setManualSplitError(dialog, '');
  renderSplitDialog(dialog, true);
}

function setManualSplitError(
  dialog: HTMLDialogElement | null,
  message: string,
) {
  if (!dialog) return;
  const input = dialog.querySelector<HTMLInputElement>(
    '.time-entry-split-manual-input',
  );
  if (!input) return;
  const error = dialog.querySelector<HTMLElement>(
    '.time-entry-split-manual-error',
  );
  const confirm = dialog.querySelector<HTMLButtonElement>(
    '.time-entry-split-confirm',
  );
  if (!error || !confirm) return;
  input.classList.toggle('is-invalid', Boolean(message));
  input.setAttribute('aria-invalid', message ? 'true' : 'false');
  error.textContent = message;
  if (message) error.style.display = 'block';
  else error.style.removeProperty('display');
  confirm.disabled = Boolean(message);
}

function beginDescriptionEdit(input: HTMLInputElement | null) {
  if (!input || input.dataset.submitting === 'true') return;
  const wrapper = input.closest<HTMLElement>('.time-entry-description-wrapper');
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

function cancelDescriptionEdit(input: HTMLInputElement | null) {
  if (!input || input.dataset.submitting === 'true') return;
  if (input.dataset.originalValue !== undefined) {
    input.value = input.dataset.originalValue;
  }
  input.readOnly = true;
  const wrapper = input.closest<HTMLElement>('.time-entry-description-wrapper');
  if (wrapper) wrapper.classList.remove('is-editing');
}

function submitDescriptionEdit(input: HTMLInputElement | null) {
  if (!input || input.readOnly || input.dataset.submitting === 'true') return;
  input.dataset.submitting = 'true';
  if (document.activeElement === input) input.blur();
  window.htmx.trigger(input, 'description-save');
}

function setRestartPending(button: HTMLButtonElement | null, pending: boolean) {
  if (!button) return;
  button.classList.toggle('is-restart-pending', pending);
  button.disabled = pending;
}

function requestEntryRestart(button: HTMLButtonElement | null) {
  if (
    !button ||
    button.disabled ||
    button.getAttribute('aria-disabled') === 'true'
  )
    return;
  const row = button.closest<HTMLElement>('.time-entry-row');
  const input =
    row &&
    row.querySelector<HTMLInputElement>('.time-entry-description-editor');
  const wrapper =
    input && input.closest<HTMLElement>('.time-entry-description-wrapper');
  const dirty =
    input &&
    !input.readOnly &&
    input.dataset.originalValue !== undefined &&
    input.value !== input.dataset.originalValue;
  const hasSaveError =
    wrapper &&
    wrapper.querySelector<HTMLElement>('.time-entry-description-error');
  const saving = input && input.dataset.submitting === 'true';
  if (dirty || hasSaveError || saving) {
    button.dataset.restartAfterDescription = 'true';
    setRestartPending(button, true);
    if (!saving) submitDescriptionEdit(input);
    return;
  }
  window.htmx.trigger(button, 'restart');
}

document.addEventListener(
  'focus',
  function (evt) {
    if (
      evt.target instanceof HTMLInputElement &&
      evt.target.matches('.time-entry-description-editor')
    ) {
      beginDescriptionEdit(evt.target);
    }
  },
  true,
);

document.addEventListener('keydown', function (evt) {
  const input = evt.target;
  if (!(input instanceof HTMLElement)) return;

  if (input.matches('.time-entry-project-search') && evt.key === 'ArrowDown') {
    const firstProject = input
      .closest<HTMLDialogElement>('.time-entry-project-dialog')
      ?.querySelector<HTMLButtonElement>(
        '.time-entry-project-option:not(:disabled)',
      );
    if (firstProject) {
      evt.preventDefault();
      firstProject.focus();
    }
    return;
  }

  if (
    input instanceof HTMLButtonElement &&
    input.matches('.time-entry-project-option') &&
    (evt.key === 'ArrowDown' || evt.key === 'ArrowUp')
  ) {
    const projectDialog = input.closest<HTMLDialogElement>(
      '.time-entry-project-dialog',
    );
    if (!projectDialog) return;
    const projectOptions = Array.from(
      projectDialog.querySelectorAll<HTMLButtonElement>(
        '.time-entry-project-option:not(:disabled)',
      ),
    );
    const projectIndex = projectOptions.indexOf(input);
    const nextProjectIndex = projectIndex + (evt.key === 'ArrowDown' ? 1 : -1);
    evt.preventDefault();
    if (nextProjectIndex >= 0 && nextProjectIndex < projectOptions.length) {
      projectOptions[nextProjectIndex]?.focus();
    } else if (nextProjectIndex < 0) {
      projectDialog
        .querySelector<HTMLInputElement>('.time-entry-project-search')
        ?.focus();
    }
    return;
  }

  if (
    !(input instanceof HTMLInputElement) ||
    !input.matches('.time-entry-description-editor')
  )
    return;

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
    if (
      evt.target instanceof HTMLInputElement &&
      evt.target.matches('.time-entry-description-editor')
    ) {
      submitDescriptionEdit(evt.target);
    }
  },
  true,
);

document.body.addEventListener('htmx:before:request', function (evt) {
  const ctx = evt.detail && evt.detail.ctx;
  const input = ctx && ctx.sourceElement;
  const startForm =
    input && input.matches && input.matches('.running-timer-start-form')
      ? input
      : input &&
        input.closest &&
        input.closest<HTMLElement>('.running-timer-start-form');
  if (startForm) restoreStartEditorFocusAfterSwap = true;
  if (
    !(input instanceof HTMLInputElement) ||
    !input.matches('.time-entry-description-editor')
  )
    return;
  input.readOnly = true;
  const wrapper = input.closest<HTMLElement>('.time-entry-description-wrapper');
  if (wrapper) wrapper.classList.add('is-saving');
});

document.body.addEventListener('htmx:after:request', function (evt) {
  const ctx = evt.detail && evt.detail.ctx;
  const input = ctx && ctx.sourceElement;
  const successful = ctx && ctx.response && ctx.response.status < 400;
  if (!(input instanceof HTMLElement)) return;
  if (
    !successful &&
    input.matches('.time-entry-split-confirm, .time-entry-split-form')
  ) {
    const splitDialog = input.closest<HTMLDialogElement>(
      '.time-entry-split-dialog',
    );
    if (!splitDialog) return;
    let splitError = splitDialog.querySelector<HTMLElement>(
      '.time-entry-split-error',
    );
    if (!splitError) {
      splitError = document.createElement('div');
      splitError.className = 'time-entry-split-error';
      splitError.setAttribute('role', 'alert');
      splitDialog
        .querySelector<HTMLElement>('.time-entry-split-dialog-body')
        ?.insertAdjacentElement('afterbegin', splitError);
    }
    splitError.textContent = 'Could not split this time entry. Try again.';
    return;
  }
  if (!successful && input.matches('.time-entry-delete-confirm')) {
    const deleteDialog = input.closest<HTMLDialogElement>(
      '.time-entry-delete-dialog',
    );
    if (!deleteDialog) return;
    let deleteError = deleteDialog.querySelector<HTMLElement>(
      '.time-entry-delete-error',
    );
    if (!deleteError) {
      deleteError = document.createElement('div');
      deleteError.className = 'time-entry-delete-error';
      deleteError.setAttribute('role', 'alert');
      deleteDialog
        .querySelector<HTMLElement>('.time-entry-delete-dialog-body')
        ?.insertAdjacentElement('afterbegin', deleteError);
    }
    deleteError.textContent = 'Could not delete this time entry. Try again.';
    return;
  }
  if (!successful && input.matches('.time-entry-project-option')) {
    const projectDialog = input.closest<HTMLDialogElement>(
      '.time-entry-project-dialog',
    );
    if (!projectDialog) return;
    let projectAlert = projectDialog.querySelector<HTMLElement>(
      '.time-entry-project-alert',
    );
    if (!projectAlert) {
      projectAlert = document.createElement('div');
      projectAlert.className = 'time-entry-project-alert';
      projectAlert.setAttribute('role', 'status');
      projectDialog
        .querySelector<HTMLElement>('.time-entry-project-dialog-header')
        ?.insertAdjacentElement('afterend', projectAlert);
    }
    projectAlert.textContent =
      'Could not save the project. Choose a project to retry.';
    return;
  }
  if (
    !(input instanceof HTMLInputElement) ||
    !input.matches('.time-entry-description-editor')
  )
    return;
  if (successful) return;
  const restartRow = input.closest<HTMLElement>('.time-entry-row');
  const restartButton =
    restartRow &&
    restartRow.querySelector<HTMLButtonElement>('.time-entry-restart-trigger');
  if (
    restartButton &&
    restartButton.dataset.restartAfterDescription === 'true'
  ) {
    delete restartButton.dataset.restartAfterDescription;
    setRestartPending(restartButton, false);
  }
  input.dataset.submitting = 'false';
  input.readOnly = false;
  const wrapper = input.closest<HTMLElement>('.time-entry-description-wrapper');
  if (wrapper) {
    wrapper.classList.remove('is-saving');
    let error = wrapper.querySelector<HTMLElement>(
      '.time-entry-description-error',
    );
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
  const ctx = evt.detail && evt.detail.ctx;
  let target: Element | string | null = ctx.target;
  if (typeof target === 'string') target = document.querySelector(target);
  disposeStartEditors(target);
});

document.body.addEventListener('htmx:after:swap', function (evt) {
  const ctx = evt.detail && evt.detail.ctx;
  let tgt: Element | string | null = ctx.target;
  if (typeof tgt === 'string') tgt = document.querySelector(tgt);
  // HTMX keeps the original target in ctx after an outerHTML replacement.
  if (tgt && !tgt.isConnected && tgt.id) {
    tgt = document.getElementById(tgt.id);
  }
  startElapsedTimers();
  initializeStartEditors(tgt || document);
  if (tgt && tgt.id === 'result' && restoreStartEditorFocusAfterSwap) {
    const updatedStartTrigger = document.querySelector<HTMLButtonElement>(
      '.running-timer-elapsed-trigger',
    );
    if (updatedStartTrigger) updatedStartTrigger.focus();
    restoreStartEditorFocusAfterSwap = false;
  } else if (tgt && tgt.matches && tgt.matches('.running-timer-start-dialog')) {
    restoreStartEditorFocusAfterSwap = false;
  }
  if (tgt && tgt.id === 'client-select-wrapper') {
    restoreClientSelection();
  }
  const deleteDialog =
    tgt &&
    tgt.querySelector &&
    tgt.querySelector<HTMLDialogElement>('.time-entry-delete-dialog');
  if (
    deleteDialog &&
    deleteDialog.getAttribute('data-open') === 'true' &&
    !deleteDialog.open
  ) {
    deleteDialog.showModal();
  }
  const splitDialog =
    tgt &&
    tgt.querySelector &&
    tgt.querySelector<HTMLDialogElement>('.time-entry-split-dialog');
  if (
    splitDialog &&
    splitDialog.getAttribute('data-open') === 'true' &&
    !splitDialog.open
  ) {
    initializeSplitDialog(splitDialog);
    splitDialog.showModal();
  }
  const descriptionInput =
    tgt &&
    tgt.querySelector &&
    tgt.querySelector<HTMLInputElement>('.time-entry-description-editor');
  if (
    descriptionInput &&
    descriptionInput.getAttribute('data-editing') === 'true'
  ) {
    beginDescriptionEdit(descriptionInput);
  }
  const descriptionWrapper =
    tgt && tgt.matches && tgt.matches('.time-entry-description-wrapper')
      ? tgt
      : tgt &&
        tgt.querySelector &&
        tgt.querySelector<HTMLElement>('.time-entry-description-wrapper');
  if (descriptionWrapper) {
    const descriptionRow =
      descriptionWrapper.closest<HTMLElement>('.time-entry-row');
    const pendingRestart =
      descriptionRow &&
      descriptionRow.querySelector<HTMLButtonElement>(
        '.time-entry-restart-trigger',
      );
    if (
      pendingRestart &&
      pendingRestart.dataset.restartAfterDescription === 'true'
    ) {
      delete pendingRestart.dataset.restartAfterDescription;
      setRestartPending(pendingRestart, false);
      if (
        !descriptionWrapper.querySelector<HTMLElement>(
          '.time-entry-description-error',
        )
      ) {
        window.htmx.trigger(pendingRestart, 'restart');
      }
    }
  }
  const projectPicker =
    tgt && tgt.matches && tgt.matches('.time-entry-project-picker')
      ? tgt
      : tgt &&
        tgt.querySelector &&
        tgt.querySelector<HTMLElement>('.time-entry-project-picker');
  if (projectPicker) {
    const projectRow = projectPicker.closest<HTMLElement>(
      '.time-entry-row, .running-timer-toolbar',
    );
    const projectColor = projectPicker.getAttribute('data-project-color');
    if (projectRow) {
      if (projectColor) {
        projectRow.style.setProperty('--project-color', projectColor);
      } else {
        projectRow.style.removeProperty('--project-color');
      }
    }
    if (projectPicker.getAttribute('data-open') === 'true') {
      openProjectPicker(
        projectPicker.querySelector<HTMLButtonElement>(
          '.time-entry-project-trigger',
        ),
      );
    }
  }
});
