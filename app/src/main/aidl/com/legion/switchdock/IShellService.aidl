package com.legion.switchdock;

interface IShellService {
    void destroy() = 16777114;
    int exec(String command);
}
