(() => {
    const modeButtons = [...document.querySelectorAll(".mode-button")];
    const codeInput = document.getElementById("employee-code");
    const video = document.getElementById("camera-preview");
    const canvas = document.getElementById("photo-canvas");
    const photo = document.getElementById("captured-photo");
    const frame = document.getElementById("camera-frame");
    const placeholder = document.getElementById("camera-placeholder");
    const cameraStatus = document.getElementById("camera-status");
    const cameraButton = document.getElementById("camera-button");
    const captureButton = document.getElementById("capture-button");
    const submitButton = document.getElementById("submit-button");
    const submitLabel = document.getElementById("submit-label");
    const message = document.getElementById("form-message");
    const form = document.getElementById("attendance-form");
    let stream;
    let capturedPhoto = "";
    let mode = "checkin";

    function updateClock() {
        const now = new Date();
        document.getElementById("current-date").textContent = new Intl.DateTimeFormat(undefined, {
            weekday: "long", month: "long", day: "numeric", year: "numeric"
        }).format(now);
        document.getElementById("current-time").textContent = new Intl.DateTimeFormat(undefined, {
            hour: "2-digit", minute: "2-digit"
        }).format(now);
    }

    function clearPhoto() {
        capturedPhoto = "";
        photo.removeAttribute("src");
        frame.classList.remove("has-photo");
        placeholder.hidden = !stream;
        submitButton.disabled = true;
        submitLabel.textContent = "Capture photo to continue";
        captureButton.textContent = "◎ Take photo";
    }

    modeButtons.forEach((button) => button.addEventListener("click", () => {
        mode = button.dataset.mode;
        modeButtons.forEach((item) => {
            const active = item === button;
            item.classList.toggle("is-active", active);
            item.setAttribute("aria-pressed", String(active));
        });
        submitLabel.textContent = mode === "checkin" ? "Confirm check-in" : "Confirm check-out";
        message.textContent = "";
        message.className = "form-message";
        clearPhoto();
    }));

    cameraButton.addEventListener("click", async () => {
        if (stream) {
            stream.getTracks().forEach((track) => track.stop());
            stream = undefined;
            video.srcObject = null;
            frame.classList.remove("is-live");
            placeholder.hidden = false;
            cameraStatus.classList.remove("is-on");
            cameraStatus.innerHTML = "<i></i> CAMERA OFF";
            cameraButton.innerHTML = "<span aria-hidden=\"true\">⌾</span> Enable camera";
            captureButton.disabled = true;
            clearPhoto();
            return;
        }
        if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
            showMessage("This browser cannot access a camera. Use HTTPS or localhost.", true);
            return;
        }
        try {
            stream = await navigator.mediaDevices.getUserMedia({
                audio: false,
                video: { facingMode: "user", width: { ideal: 1280 }, height: { ideal: 720 } }
            });
            video.srcObject = stream;
            await video.play();
            frame.classList.add("is-live");
            placeholder.hidden = true;
            cameraStatus.classList.add("is-on");
            cameraStatus.innerHTML = "<i></i> CAMERA READY";
            cameraButton.innerHTML = "<span aria-hidden=\"true\">×</span> Turn off camera";
            captureButton.disabled = false;
            showMessage("", false);
        } catch (error) {
            showMessage(error.name === "NotAllowedError"
                ? "Camera access was blocked. Allow camera access and try again."
                : "Unable to start the camera. Check that a camera is connected.", true);
        }
    });

    captureButton.addEventListener("click", () => {
        if (!stream || !video.videoWidth) return;
        const scale = Math.min(1, 960 / video.videoWidth);
        canvas.width = Math.round(video.videoWidth * scale);
        canvas.height = Math.round(video.videoHeight * scale);
        const context = canvas.getContext("2d", { alpha: false });
        context.translate(canvas.width, 0);
        context.scale(-1, 1);
        context.drawImage(video, 0, 0, canvas.width, canvas.height);
        capturedPhoto = canvas.toDataURL("image/jpeg", 0.78);
        photo.src = capturedPhoto;
        frame.classList.add("has-photo");
        submitButton.disabled = false;
        submitLabel.textContent = mode === "checkin" ? "Confirm check-in" : "Confirm check-out";
        captureButton.innerHTML = "<span aria-hidden=\"true\">◉</span> Retake photo";
        showMessage("Photo ready. Confirm to record your attendance.", false);
    });

    form.addEventListener("submit", async (event) => {
        event.preventDefault();
        const employeeCode = codeInput.value.trim().toUpperCase();
        if (!employeeCode) {
            showMessage("Enter your employee code.", true);
            codeInput.focus();
            return;
        }
        if (!capturedPhoto) {
            showMessage("Take a photo before continuing.", true);
            return;
        }
        submitButton.disabled = true;
        submitLabel.textContent = "Recording attendance...";
        try {
            const response = await fetch("api/attendance", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ employeeCode, action: mode, photo: capturedPhoto })
            });
            const result = await response.json();
            if (!response.ok) throw new Error(result.error || "Attendance could not be recorded.");
            showMessage(result.message, false, true);
            codeInput.value = "";
            clearPhoto();
        } catch (error) {
            showMessage(error.message || "The server could not be reached.", true);
            submitButton.disabled = !capturedPhoto;
            submitLabel.textContent = mode === "checkin" ? "Confirm check-in" : "Confirm check-out";
        }
    });

    function showMessage(text, isError, isSuccess) {
        message.textContent = text;
        message.className = "form-message" + (isError ? " is-error" : "") + (isSuccess ? " is-success" : "");
    }

    updateClock();
    window.setInterval(updateClock, 30000);
    window.addEventListener("pagehide", () => {
        if (stream) stream.getTracks().forEach((track) => track.stop());
    });
})();