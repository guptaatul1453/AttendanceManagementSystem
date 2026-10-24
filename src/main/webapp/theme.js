(() => {
    const themeButtons = [...document.querySelectorAll(".theme-button")];
    const themeColor = document.querySelector('meta[name="theme-color"]');

    function setTheme(theme, persist = true) {
        document.documentElement.dataset.theme = theme;
        themeButtons.forEach((button) => {
            const active = button.dataset.themeChoice === theme;
            button.classList.toggle("is-active", active);
            button.setAttribute("aria-pressed", String(active));
        });
        themeColor.content = theme === "dark" ? "#101e26" : "#edf3f2";
        if (persist) localStorage.setItem("attendance-theme", theme);
    }

    setTheme(localStorage.getItem("attendance-theme") === "dark" ? "dark" : "light");
    themeButtons.forEach((button) => button.addEventListener("click", () => {
        setTheme(button.dataset.themeChoice);
    }));
    window.addEventListener("storage", (event) => {
        if (event.key === "attendance-theme" && (event.newValue === "light" || event.newValue === "dark")) {
            setTheme(event.newValue, false);
        }
    });
})();
