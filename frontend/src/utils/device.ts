const DEVICE_ID_KEY = 'device_id';

export const getDeviceId = (): string => {
    const stored = localStorage.getItem(DEVICE_ID_KEY);
    if (stored) return stored;
    const id = crypto.randomUUID();
    localStorage.setItem(DEVICE_ID_KEY, id);
    return id;
};

export const PLATFORM = 'web';