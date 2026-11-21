package com.example.shizuku;

interface IShizukuShellService {
    void destroy() = 16777114;
    String execCommand(String command) = 1;
}
