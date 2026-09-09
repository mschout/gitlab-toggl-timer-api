// Runtime libraries are provided by WebJars; these imports resolve declarations only.
interface Window {
  htmx: typeof import('htmx.org').default;
  AirDatepicker?: typeof import('air-datepicker').default;
  bootstrap: typeof import('bootstrap');
  setupLogin?: (
    headers: Record<string, string>,
    contextPath: string,
    button: HTMLButtonElement,
  ) => void;
  __togglElapsedIntervalId?: number;
}

interface RunningTimerStartDialog extends HTMLDialogElement {
  _runningTimerDatepicker?: import('air-datepicker').default<HTMLElement>;
}
