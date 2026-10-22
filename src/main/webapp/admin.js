(() => {
    const adminBase = new URL("admin/", window.location.href);
    const loginView = document.getElementById("login-view");
    const workspace = document.getElementById("workspace");
    const loginForm = document.getElementById("login-form");
    const loginMessage = document.getElementById("login-message");
    const logoutButton = document.getElementById("logout-button");
    const attendanceRows = document.getElementById("attendance-rows");
    const attendanceMessage = document.getElementById("attendance-message");
    const employeeList = document.getElementById("employee-list");
    const employeeMessage = document.getElementById("employee-message");
    const modal = document.getElementById("photo-modal");
    let loadedRows = [];

    function endpoint(path) {
        return new URL(path, adminBase).toString();
    }

    async function request(path, options) {
        const response = await fetch(endpoint(path), { credentials: "same-origin", ...options });
        const result = await response.json();
        if (!response.ok) throw new Error(result.error || "The request could not be completed.");
        return result;
    }

    function showWorkspace() {
        loginView.classList.add("hidden");
        workspace.classList.remove("hidden");
        logoutButton.classList.remove("hidden");
        document.getElementById("admin-date").textContent = new Intl.DateTimeFormat(undefined, {
            weekday: "short", month: "short", day: "numeric", year: "numeric"
        }).format(new Date());
    }

    function showLogin() {
        workspace.classList.add("hidden");
        loginView.classList.remove("hidden");
        logoutButton.classList.add("hidden");
    }

    loginForm.addEventListener("submit", async (event) => {
        event.preventDefault();
        const button = loginForm.querySelector("button[type=submit]");
        button.disabled = true;
        loginMessage.textContent = "";
        try {
            await request("login", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ password: document.getElementById("admin-password").value })
            });
            loginForm.reset();
            showWorkspace();
            await loadAttendance();
            await loadEmployees();
        } catch (error) {
            loginMessage.textContent = error.message;
        } finally {
            button.disabled = false;
        }
    });

    logoutButton.addEventListener("click", async () => {
        try { await request("logout", { method: "POST" }); } catch (error) { /* Session may already be gone. */ }
        showLogin();
    });

    async function loadAttendance() {
        const params = new URLSearchParams();
        const query = document.getElementById("filter-query").value.trim();
        const date = document.getElementById("filter-date").value;
        const status = document.getElementById("filter-status").value;
        if (query) params.set("q", query);
        if (date) params.set("date", date);
        if (status !== "all") params.set("status", status);
        attendanceMessage.textContent = "";
        attendanceRows.innerHTML = '<tr><td colspan="6" class="empty-row">Loading attendance...</td></tr>';
        try {
            const result = await request("api/attendance?" + params.toString());
            loadedRows = result.rows;
            renderAttendance(loadedRows);
        } catch (error) {
            attendanceRows.innerHTML = '<tr><td colspan="6" class="empty-row">Attendance could not be loaded.</td></tr>';
            attendanceMessage.textContent = error.message;
            if (error.message === "Admin login required.") showLogin();
        }
    }

    function renderAttendance(rows) {
        attendanceRows.replaceChildren();
        document.getElementById("filter-count").textContent = rows.length + (rows.length === 250 ? "+ rows" : " rows");
        document.getElementById("stat-records").textContent = rows.length;
        document.getElementById("stat-open").firstChild.textContent = rows.filter((row) => !row.checkout).length;
        document.getElementById("stat-complete").textContent = rows.filter((row) => row.checkout).length;
        if (!rows.length) {
            const empty = document.createElement("tr");
            empty.innerHTML = '<td colspan="6" class="empty-row">No attendance records match these filters.</td>';
            attendanceRows.append(empty);
            return;
        }
        rows.forEach((row) => {
            const tr = document.createElement("tr");
            const employee = document.createElement("td");
            employee.innerHTML = "<div class=\"employee-cell\"><strong></strong><span></span></div>";
            employee.querySelector("strong").textContent = row.fullName;
            employee.querySelector("span").textContent = row.employeeCode + " · " + row.department;
            tr.append(employee);
            tr.append(textCell(formatDate(row.checkin)));
            tr.append(textCell(row.checkout ? formatDate(row.checkout) : "—"));
            const state = document.createElement("td");
            const pill = document.createElement("span");
            pill.className = "status-pill" + (row.checkout ? "" : " open");
            pill.textContent = row.checkout ? "Complete" : "Checked in";
            state.append(pill);
            tr.append(state);
            tr.append(photoCell(row.id, "checkin"));
            tr.append(photoCell(row.id, "checkout", Boolean(row.checkout)));
            attendanceRows.append(tr);
        });
    }

    function textCell(text) {
        const cell = document.createElement("td");
        cell.textContent = text;
        return cell;
    }

    function photoCell(id, type, available = true) {
        const cell = document.createElement("td");
        if (!available) {
            const empty = document.createElement("span");
            empty.className = "photo-empty";
            empty.textContent = "—";
            cell.append(empty);
            return cell;
        }
        const button = document.createElement("button");
        button.type = "button";
        button.setAttribute("aria-label", "Expand " + type + " photo");
        button.style.cssText = "display:block;padding:0;border:0;background:none";
        const image = document.createElement("img");
        image.className = "photo-thumb";
        image.alt = "";
        image.loading = "lazy";
        image.src = endpoint("api/photo?id=" + encodeURIComponent(id) + "&type=" + type);
        image.addEventListener("error", () => { button.replaceWith(Object.assign(document.createElement("span"), {
            className: "photo-empty", textContent: "Unavailable"
        })); }, { once: true });
        button.append(image);
        button.addEventListener("click", () => openPhoto(image.src));
        cell.append(button);
        return cell;
    }

    function formatDate(value) {
        return value.replace("T", " ").slice(0, 16);
    }

    async function loadEmployees() {
        try {
            const result = await request("api/employees");
            document.getElementById("employee-count").textContent = result.employees.length + " PEOPLE";
            employeeList.replaceChildren();
            if (!result.employees.length) {
                employeeList.innerHTML = '<div class="employee-row">No employee profiles yet.</div>';
                return;
            }
            result.employees.forEach((employee) => {
                const item = document.createElement("div");
                item.className = "employee-row";
                const details = document.createElement("div");
                const name = document.createElement("strong");
                name.textContent = employee.fullName;
                const department = document.createElement("span");
                department.textContent = employee.department;
                const code = document.createElement("span");
                code.className = "employee-code";
                code.textContent = employee.employeeCode;
                details.append(name, department);
                item.append(details, code);
                employeeList.append(item);
            });
        } catch (error) {
            employeeList.innerHTML = '<div class="employee-row">Employee list could not be loaded.</div>';
            employeeMessage.textContent = error.message;
        }
    }

    document.getElementById("employee-form").addEventListener("submit", async (event) => {
        event.preventDefault();
        employeeMessage.textContent = "Creating employee...";
        const form = event.currentTarget;
        const button = form.querySelector("button[type=submit]");
        button.disabled = true;
        try {
            const result = await request("api/employees", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({
                    fullName: document.getElementById("employee-name").value,
                    department: document.getElementById("employee-department").value,
                    email: document.getElementById("employee-email").value
                })
            });
            form.reset();
            employeeMessage.textContent = result.fullName + " added with code " + result.employeeCode + ".";
            await loadEmployees();
        } catch (error) {
            employeeMessage.textContent = error.message;
        } finally {
            button.disabled = false;
        }
    });

    document.querySelectorAll(".side-nav button").forEach((button) => button.addEventListener("click", () => {
        document.querySelectorAll(".side-nav button").forEach((item) => item.classList.toggle("active", item === button));
        document.querySelectorAll(".admin-view").forEach((view) => view.classList.toggle("hidden", view.id !== button.dataset.view));
    }));

    let searchTimer;
    document.getElementById("filter-query").addEventListener("input", () => {
        window.clearTimeout(searchTimer);
        searchTimer = window.setTimeout(loadAttendance, 260);
    });
    document.getElementById("filter-date").addEventListener("change", loadAttendance);
    document.getElementById("filter-status").addEventListener("change", loadAttendance);

    function openPhoto(src) {
        document.getElementById("modal-image").src = src;
        modal.classList.add("is-open");
    }
    function closePhoto() {
        modal.classList.remove("is-open");
        document.getElementById("modal-image").removeAttribute("src");
    }
    document.getElementById("modal-close").addEventListener("click", closePhoto);
    modal.addEventListener("click", (event) => { if (event.target === modal) closePhoto(); });
    window.addEventListener("keydown", (event) => { if (event.key === "Escape") closePhoto(); });

    request("api/attendance").then((result) => {
        showWorkspace();
        loadedRows = result.rows;
        renderAttendance(loadedRows);
        loadEmployees();
    }).catch((error) => {
        showLogin();
        if (error.message !== "Admin login required.") loginMessage.textContent = error.message;
    });
})();