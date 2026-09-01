export const initKuikly: () => number;
export const dshHttpPostJson: (url: string, body: string, token: string) => Promise<string>;
export const setMainHandler: (fn: (raw: string) => void) => void;
export const postToMain: (raw: string) => void;
