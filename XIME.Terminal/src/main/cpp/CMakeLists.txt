cmake_minimum_required(VERSION 3.22.1)
project("xime-terminal")

add_library(xime-terminal SHARED
        pty.c
        externs/termux/termux.c)

find_library(log-lib log)
find_library(util-lib util)

if(util-lib)
    set(PTY_LIBS ${util-lib})
else()
    # On some Android versions/NDKs, forkpty/openpty are in libc or not available
    set(PTY_LIBS "")
endif()

target_link_libraries(xime-terminal
        ${log-lib}
        ${PTY_LIBS})

