package com.legion.switchdock;

import com.legion.switchdock.IUhidListener;

interface IShellService {
    void destroy() = 16777114;
    int exec(String command) = 1;
    /** Creates a virtual HID device (/dev/uhid). Returns a handle > 0, or <= 0 on failure (see lastError). */
    int uhidCreate(String name, int vendor, int product, in byte[] descriptor) = 2;
    /** Same, choosing the bus (3 = USB, 5 = Bluetooth). */
    int uhidCreateOnBus(String name, int bus, int vendor, int product, in byte[] descriptor) = 8;
    oneway void uhidInput(int handle, in byte[] report) = 3;
    void uhidDestroy(int handle) = 4;
    void uhidDestroyAll() = 5;
    String lastError() = 6;
    void uhidSetListener(IUhidListener listener) = 7;
}
