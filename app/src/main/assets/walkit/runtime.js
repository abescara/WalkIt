(function () {
  const bridge = window.WalkIt;
  const fileName = decodeURIComponent(location.pathname.split("/").pop().replace(/\.html$/i, ""));
  const screenName = fileName.endsWith("_Dark") ? fileName.slice(0,-5) : fileName;

  function sizeToDevice() {
    const wrap = document.querySelector(".wrap");
    const canvas = wrap && wrap.firstElementChild;
    if (!wrap || !canvas) return;
    const scale = window.innerWidth / 390;
    const width = 390 * scale;
    const height = window.innerHeight;
    const logicalHeight = height / scale;
    document.documentElement.style.cssText = "width:100%;height:100%;overflow:hidden";
    document.body.style.cssText = "margin:0;width:100%;height:100%;overflow:hidden;display:flex;align-items:center;justify-content:center;background:transparent";
    wrap.style.cssText = `width:${width}px;height:${height}px;margin:0;overflow:hidden;flex:none`;
    canvas.style.transform = `scale(${scale})`;
    canvas.style.transformOrigin = "top left";
    canvas.style.width = "390px";
    canvas.style.height = `${logicalHeight}px`;
  }

  function labelFor(button) {
    return (button.getAttribute("aria-label") || button.innerText || button.textContent || "").trim().replace(/\s+/g, " ");
  }

  function setSelected(button, peers) {
    peers.forEach((peer) => {
      peer.style.background = peer === button ? "#2b2b2b" : "#fff";
      peer.style.color = peer === button ? "#fff" : "#333";
      peer.style.borderColor = peer === button ? "#2b2b2b" : "#d0d0d0";
      peer.setAttribute("aria-pressed", peer === button ? "true" : "false");
    });
  }

  function readDraft() {
    try { return JSON.parse(localStorage.getItem("walkit-report-draft") || "{}"); }
    catch (_) { return {}; }
  }

  function saveDraft(value) {
    try { localStorage.setItem("walkit-report-draft",JSON.stringify(value)); }
    catch (_) {}
  }

  function clearDraft() {
    try { localStorage.removeItem("walkit-report-draft"); }
    catch (_) {}
  }

  function hydrateReport() {
    if (!bridge || screenName !== "Report") return;
    try {
      const draft = readDraft();
      const detailField = document.getElementById("report-details");
      if (detailField && draft.details) detailField.value = draft.details;
      if (draft.category) window.walkItCategory = draft.category;
      const photos = JSON.parse(bridge.getDraftPhotos());
      const image = document.getElementById("captured-preview");
      if (image && photos.length) image.src = "file://" + photos[0];
      const thumbnails = document.getElementById("report-photo-thumbs");
      if (thumbnails) renderThumbnails(thumbnails,photos,48);
      const label = document.getElementById("report-location-label");
      if (label) label.textContent = bridge.getDraftCoordinates() + " · tap to adjust pin";
    } catch (_) {}
  }

  function hydratePreview() {
    if (!bridge || screenName !== "Report_Preview") return;
    try {
      const photos = JSON.parse(bridge.getSubmittedPhotos());
      const image = document.getElementById("submitted-preview");
      if (image && photos.length) image.src = "file://" + photos[0];
      const thumbnails = document.getElementById("submitted-photo-thumbs");
      if (thumbnails) renderThumbnails(thumbnails,photos,42);
      const description = document.getElementById("submitted-description");
      if (description) description.textContent = bridge.getSubmittedDescription();
      const category = document.getElementById("submitted-category");
      if (category) category.textContent = bridge.getSubmittedDescription().split("·")[0].trim();
      const location = document.getElementById("submitted-location");
      if (location) location.textContent = bridge.getSubmittedLocation();
    } catch (_) {}
  }

  function renderThumbnails(container,photos,size) {
    container.replaceChildren();
    if(photos.length<2) { container.style.display="none"; return; }
    container.style.display="flex";
    container.style.gap="8px";
    container.style.overflowX="auto";
    container.style.height=(size+8)+"px";
    photos.forEach((path)=>{
      const image=document.createElement("img");
      image.src="file://"+path;
      image.alt="Additional report photo";
      image.style.cssText=`width:${size}px;height:${size}px;object-fit:cover;border-radius:10px;flex:none;border:2px solid #fff;box-shadow:0 1px 5px #0002`;
      container.appendChild(image);
    });
  }

  document.addEventListener("click", function (event) {
    const locationSearch = event.target.closest("#search-location");
    if (locationSearch && bridge) { bridge.searchLocation(); return; }
    const locationPin = event.target.closest("#report-location");
    if (locationPin && bridge) { bridge.changeReportLocation(); return; }

    const button = event.target.closest("button");
    if (!button) {
      const profileIcon = event.target.closest('[aria-label="Profile"]');
      if (profileIcon && bridge) bridge.navigate("profile");
      return;
    }
    const label = labelFor(button);
    const lower = label.toLowerCase();
    if (lower === "pin route start on map" || lower === "pin route destination on map") {
      const start=document.getElementById("route-origin");
      const end=document.getElementById("route-destination");
      localStorage.setItem("walkit-route-fields",JSON.stringify({start:start?.value || "",end:end?.value || ""}));
      if (bridge) bridge.pickRoutePoint(lower.includes("start") ? "start" : "end");
      return;
    }

    if (button.id === "add-report-photo") { if (bridge) bridge.addReportPhotos(); return; }

    if (screenName === "Camera") {
      const action = (button.getAttribute("aria-label") || "").toLowerCase();
      if (action === "close camera") { if (bridge) bridge.closeCamera(); return; }
      if (action === "take photo") { if (bridge) bridge.captureCameraPhoto(); return; }
      if (action === "open gallery") { if (bridge) bridge.openCameraGallery(); return; }
      if (action === "flip camera") { if (bridge) bridge.flipCamera(); return; }
      if (action === "flash") { if (bridge) bridge.toggleCameraFlash(); return; }
      if (action === "done taking photos") { if (bridge) bridge.finishCamera(); return; }
      if (action === "single photo mode" || action === "batch photo mode") {
        const buttons = Array.from(document.querySelectorAll('[aria-label="Single photo mode"],[aria-label="Batch photo mode"]'));
        const batch = action === "batch photo mode";
        buttons.forEach((item) => {
          const active = item === button;
          item.style.background = active ? "#fff" : "transparent";
          item.style.color = active ? "#111" : "#fff";
          item.setAttribute("aria-pressed", active ? "true" : "false");
        });
        if (bridge) {
          bridge.setCameraBatchMode(batch);
          window.walkItUpdateCameraState(bridge.getCameraPhotoCount(),batch);
        }
        return;
      }
    }

    if (["map", "community", "routes", "rewards"].includes(lower)) {
      if(screenName==="Report" && lower==="map") clearDraft();
      if (bridge) bridge.navigate(lower);
      return;
    }
    if (lower === "report an issue" || lower === "take photo" || lower === "retake") {
      if (bridge) bridge.openCamera();
      return;
    }
    if (lower === "notifications") { if (bridge) bridge.navigate("notifications"); return; }
    if (lower === "friends") { if (bridge) bridge.navigate("friends"); return; }
    if (lower === "profile" || button.getAttribute("aria-label") === "Profile") { if (bridge) bridge.navigate("profile"); return; }
    if (lower === "edit profile") { if (bridge) bridge.navigate("edit_profile"); return; }
    if (lower === "leaderboard" || lower === "nationwide") {
      if (bridge) bridge.navigate(lower === "nationwide" ? "leaderboard_nationwide" : "leaderboard");
      return;
    }
    if (lower === "badges") { if (bridge) bridge.navigate("rewards"); return; }
    if (lower === "post on forum") { if (bridge) bridge.navigate("community"); return; }
    if (lower === "find route" || lower === "start detour") {
      const origin = document.getElementById("route-origin");
      const destination = document.getElementById("route-destination");
      if (!origin || !destination || !origin.value.trim() || !destination.value.trim()) {
        if (bridge) bridge.showMessage("Enter a start and destination as latitude, longitude.");
        return;
      }
      document.getElementById("route-status").textContent = "Finding a walking route…";
      if (bridge) bridge.planRoute(origin.value.trim(),destination.value.trim());
      return;
    }
    if (lower === "done" || lower === "back to map" || lower === "home") { if (bridge) bridge.navigate("map"); return; }
    if (lower === "create a forum") { if (bridge) bridge.showMessage("Forum posting will be enabled when the community service is connected."); return; }
    if (lower === "save changes") { if (bridge) bridge.showMessage("Profile editing is a visual prototype in this build."); return; }
    if (lower === "change photo") { if (bridge) bridge.showMessage("Profile photo editing will be connected with profile storage."); return; }
    if (lower === "light mode" || lower === "dark mode") { if (bridge) bridge.setDarkMode(lower === "dark mode"); return; }

    if (screenName === "Report" && ["broken sidewalk", "blocked path", "no ramp", "poor lighting"].includes(lower)) {
      window.walkItCategory = label;
      saveDraft({category:label,details:(document.getElementById("report-details") || {}).value || ""});
      const chips = Array.from(document.querySelectorAll("button")).filter((item) => ["broken sidewalk", "blocked path", "no ramp", "poor lighting"].includes(labelFor(item).toLowerCase()));
      setSelected(button, chips);
      return;
    }
    if (screenName === "Report" && lower === "place report on map") {
      const detail = document.getElementById("report-details");
      if (!detail || !detail.value.trim()) { if (detail) { detail.focus(); detail.placeholder = "Add a short description first"; } return; }
      clearDraft();
      if (bridge) bridge.submitReport(window.walkItCategory || "Broken sidewalk", detail.value.trim());
      return;
    }
    if (["forum", "local updates", "all", "reports", "saved places", "this week", "wheelchair access", "fewer stairs", "smooth path", "well lit", "still there", "fixed", "needs details"].includes(lower)) {
      const group = button.parentElement ? Array.from(button.parentElement.querySelectorAll("button")) : [button];
      setSelected(button, group);
      return;
    }
    if (bridge) bridge.showMessage(label + " is shown as a prototype control.");
  });

  document.addEventListener("input",function(event){
    if(screenName==="Routes" && (event.target?.id==="route-origin" || event.target?.id==="route-destination")) {
      localStorage.setItem("walkit-route-fields",JSON.stringify({start:document.getElementById("route-origin")?.value || "",end:document.getElementById("route-destination")?.value || ""}));
    }
    if(screenName==="Report" && event.target && event.target.id==="report-details") {
      saveDraft({category:window.walkItCategory || "Broken sidewalk",details:event.target.value});
    }
  });

  window.addEventListener("resize", sizeToDevice);
  window.walkItRefreshDraft = hydrateReport;
  window.walkItRefreshPreview = hydratePreview;
  window.walkItClearDraft = clearDraft;
  window.walkItRouteReady = function(summary,warning) {
    const status=document.getElementById("route-status");
    const details=document.getElementById("route-summary");
    if(status) status.textContent=warning || "Walking route is ready on the map";
    if(details) details.textContent=summary;
  };
  window.walkItRouteError = function(message) {
    const status=document.getElementById("route-status");
    if(status) status.textContent=message;
  };
  window.walkItSetFlash = function(enabled) {
    const flash = document.querySelector('[aria-label="Flash"]');
    if (flash) flash.style.background = enabled ? "#facc15" : "#0009";
  };
  window.walkItUpdateCameraState = function(count,batch) {
    const done = document.getElementById("camera-done");
    if (done) { done.textContent = `Done ${count}`; done.style.display = batch && count > 0 ? "block" : "none"; }
    const modes = [document.querySelector('[aria-label="Single photo mode"]'),document.querySelector('[aria-label="Batch photo mode"]')];
    modes.forEach((button,index) => {
      if (!button) return;
      const active = (index === 1) === batch;
      button.style.background = active ? "#fff" : "transparent";
      button.style.color = active ? "#111" : "#fff";
    });
  };
  sizeToDevice();
  hydrateReport();
  hydratePreview();
  if(screenName === "Routes" && bridge) {
    const origin=document.getElementById("route-origin");
    const destination=document.getElementById("route-destination");
    let saved={};
    try { saved=JSON.parse(localStorage.getItem("walkit-route-fields") || "{}"); } catch (_) {}
    if(origin) origin.value=bridge.getPinnedRoutePoint("start") || saved.start || bridge.getRouteOrigin();
    if(destination) destination.value=bridge.getPinnedRoutePoint("end") || saved.end || "";
  }
  if (screenName === "Camera" && bridge) window.walkItUpdateCameraState(bridge.getCameraPhotoCount(),bridge.isCameraBatchMode());
})();
