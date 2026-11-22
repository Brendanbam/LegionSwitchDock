package com.legion.switchdock;

interface IUhidListener {
    /** Output report the system sent to a virtual device (e.g. rumble). */
    oneway void onOutput(int handle, in byte[] data);
}
