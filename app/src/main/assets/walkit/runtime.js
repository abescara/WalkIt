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
    const darkReport = screenName === "Report" && fileName.endsWith("_Dark");
    peers.forEach((peer) => {
      peer.style.background = peer === button ? (darkReport ? "#f2f2f2" : "#2b2b2b") : (darkReport ? "#1e1e1e" : "#fff");
      peer.style.color = peer === button ? (darkReport ? "#111" : "#fff") : (darkReport ? "#e0e0e0" : "#333");
      peer.style.borderColor = peer === button ? (darkReport ? "#f2f2f2" : "#2b2b2b") : (darkReport ? "#3a3a3a" : "#d0d0d0");
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
      const chips=Array.from(document.querySelectorAll("button")).filter(item=>["broken sidewalk","blocked path","no ramp","poor lighting"].includes(labelFor(item).toLowerCase()));
      setSelected(chips.find(item=>labelFor(item)===window.walkItCategory) || chips[0],chips);
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

  function renderAccountPage() {
    if (!bridge || !["Login","Signup","Profile","Edit_Profile","Leaderboard","Leaderboard_Nationwide"].includes(screenName)) return;
    const canvas=document.querySelector(".wrap > div");
    if (!canvas) return;
    const dark=fileName.endsWith("_Dark");
    const leaf=(text)=>Array.from(canvas.querySelectorAll("div,h2,p,span")).find(item=>item.children.length===0 && item.textContent.trim()===text);
    const replaceField=(element,id,type,placeholder,value="")=>{
      if(!element) return;
      const field=document.createElement("input");
      field.id=id; field.type=type; field.placeholder=placeholder; field.value=value;
      field.style.cssText=element.style.cssText+`;width:100%;box-sizing:border-box;outline:none;font:14px Inter,sans-serif;color:${dark?'#f2f2f2':'#1a1a1a'}`;
      element.replaceWith(field);
    };
    if(screenName==="Login" || screenName==="Signup") {
      const form=canvas.querySelector('div[style*="top:110px"],div[style*="top:84px"]');
      if(form) form.style.cssText+=";bottom:8px;overflow-y:auto;scrollbar-width:none";
      if(screenName==="Signup") {
        const name=leaf("Juan Dela Cruz");
        replaceField(name,"account-name","text","Full name");
        const username=document.createElement("div");
        username.innerHTML=`<div style="font-size:12px;font-weight:600;color:${dark?'#ddd':'#444'};margin:12px 0 6px 6px">Username</div><input id="account-username" type="text" placeholder="Choose a username" autocapitalize="none" style="width:100%;height:52px;border:1px solid ${dark?'#555':'#cfcfcf'};border-radius:26px;padding:0 20px;box-sizing:border-box;font:14px Inter,sans-serif;background:${dark?'#262626':'#fff'};color:${dark?'#fff':'#1a1a1a'}">`;
        document.getElementById("account-name")?.insertAdjacentElement("afterend",username);
      }
      replaceField(leaf("you@email.com"),"account-email","email","you@email.com");
      const passwordBoxes=Array.from(canvas.querySelectorAll("div")).filter(item=>item.style.height==="52px" && item.textContent.includes("••"));
      passwordBoxes.forEach((box,index)=>{
        box.textContent="";
        const field=document.createElement("input");
        field.id=index===0?"account-password":"account-confirm"; field.type="password";
        field.placeholder=index===0?"At least 12 characters":"Repeat password";
        field.style.cssText=`flex:1;min-width:0;border:0;outline:none;background:transparent;font:14px Inter,sans-serif;color:${dark?'#fff':'#1a1a1a'}`;
        box.appendChild(field);
        if(index===0) {
          const show=document.createElement("button"); show.type="button"; show.id="show-account-password"; show.textContent="Show";
          show.style.cssText="border:0;background:none;color:#3d7f12;font:600 12px Inter,sans-serif;padding:8px";
          box.appendChild(show);
        }
      });
      const submit=Array.from(canvas.querySelectorAll("button")).find(item=>item.textContent.trim()===(screenName==="Login"?"Log In":"Sign Up") && !item.closest("div[style*=\"text-align:center\"]"));
      if(submit) submit.id=screenName==="Login"?"login-submit":"signup-submit";
      const message=document.createElement("div"); message.id="account-message"; message.setAttribute("role","status");
      message.style.cssText="min-height:20px;margin-top:8px;color:#b42318;font:12px Inter,sans-serif";
      submit?.insertAdjacentElement("afterend",message);
      return;
    }
    if(screenName==="Profile" || screenName==="Edit_Profile") {
      let profile={}; try { profile=JSON.parse(bridge.getProfile()); } catch (_) {}
      const points=Number(profile.points)||0;
      const tier=points>=200?"Platinum":points>=100?"Gold":points>=50?"Silver":"Bronze";
      const next=points>=200?null:points>=100?200:points>=50?100:50;
      if(screenName==="Profile") {
        const set=(old,value)=>{ const item=leaf(old); if(item) item.textContent=value; };
        set("Juan Dela Cruz",profile.displayName||"Walker"); set("@juan.walks","@"+(profile.username||""));
        set("Walking Sampaloc daily and reporting broken paths so everyone can get around safely.",profile.bio||"No bio yet.");
        set("12",profile.reports||0); set("34",0); set("4",0); set("#3",profile.rank?"#"+profile.rank:"—");
        const title=Array.from(canvas.querySelectorAll("span")).find(item=>item.textContent.includes("Gold · Level 6"));
        if(title) title.lastChild.textContent=tier;
        const gold=leaf("Gold"); if(gold) gold.textContent=tier;
        set("Next: Platinum",next?`Next: ${next===50?"Silver":next===100?"Gold":"Platinum"}`:"Highest title");
        set("124 / 200 points",next?`${points} / ${next} points`:`${points} points`);
        const progress=canvas.querySelector('div[style*="width:62%"]');
        if(progress) progress.style.width=next?Math.round(points/next*100)+"%":"100%";
      } else {
        canvas.querySelectorAll('[aria-label="Profile picture"], img[alt="Change photo"]').forEach(item=>item.style.cursor="pointer");
        replaceField(leaf("Juan Dela Cruz"),"profile-name","text","Display name",profile.displayName||"");
        const username=leaf("@juan.walks"); if(username) username.textContent="@"+(profile.username||"");
        const bio=leaf("Walking Sampaloc daily and reporting broken paths so everyone can get around safely.");
        if(bio) {
          const field=document.createElement("textarea"); field.id="profile-bio"; field.maxLength=300; field.value=profile.bio||"";
          field.style.cssText=bio.style.cssText+`;width:100%;resize:none;background:${dark?'#262626':'#fff'};color:${dark?'#fff':'#1a1a1a'}`; bio.replaceWith(field);
        }
        const hint=leaf("✓ Username is available"); if(hint) hint.textContent="Username is set on registration";
        const save=Array.from(canvas.querySelectorAll("button")).find(item=>item.textContent.trim()==="Save changes");
        if(save) save.id="profile-save";
      }
      if(screenName==="Profile") {
        const edit=Array.from(canvas.querySelectorAll("button")).find(item=>item.textContent.trim()==="Edit profile");
        if(edit) {
          const logout=document.createElement("button"); logout.id="profile-logout"; logout.textContent="Log out";
          logout.style.cssText="position:absolute;left:16px;right:16px;top:338px;height:36px;border:0;background:none;color:#3d7f12;font:600 13px Inter,sans-serif";
          canvas.appendChild(logout);
        }
      }
      window.walkItRefreshProfilePhoto();
      return;
    }
    if(screenName.startsWith("Leaderboard")) renderDesignedLeaderboard(canvas);
  }

  function renderDesignedLeaderboard(canvas) {
    let entries=[]; try { entries=JSON.parse(bridge.getLeaderboard()); } catch (_) {}
    const dark=fileName.endsWith("_Dark"), current=bridge.getCurrentUserId();
    const filters=Array.from(canvas.querySelectorAll("button")).filter(item=>["Friends","Nationwide","This week"].includes(item.textContent.trim()));
    filters.forEach(item=>{
      const selected=item.textContent.trim()==="Nationwide";
      item.style.background=selected?(dark?"#f2f2f2":"#2b2b2b"):(dark?"#242424":"#fff");
      item.style.color=selected?(dark?"#111":"#fff"):(dark?"#f2f2f2":"#333");
      item.setAttribute("aria-pressed",selected?"true":"false");
    });
    Array.from(canvas.children).forEach(item=>{
      if(item.style.top && Number.parseInt(item.style.top,10)>=218 && item.style.bottom!=="0px") item.remove();
    });
    const list=document.createElement("div");
    list.style.cssText=`position:absolute;top:218px;left:16px;right:16px;bottom:72px;overflow-y:auto;scrollbar-width:none;color:${dark?'#f2f2f2':'#1a1a1a'}`;
    list.innerHTML=`<div style="font-size:14px;font-weight:700;margin:4px 0 14px">WalkIt leaderboard</div><div style="font-size:12px;opacity:.7;margin-bottom:12px">10 points per uploaded report</div>`+
      (entries.length?entries.map(entry=>`<div style="display:flex;align-items:center;gap:12px;padding:14px;margin:8px 0;border-radius:16px;background:${entry.userId===current?'#dff5d2':dark?'#2b2b2b':'#f4f4f4'};color:${entry.userId===current?'#17230f':'inherit'}"><b style="width:32px">#${entry.rank}</b><div style="flex:1;min-width:0"><b>${escapeHtml(entry.displayName)}</b><div style="font-size:12px;opacity:.7">@${escapeHtml(entry.username)} · ${entry.reports} reports</div></div><b>${entry.points} pts</b></div>`).join(""):'<p>No ranked accounts yet. Submit a report to earn points.</p>');
    canvas.appendChild(list);
  }

  function escapeHtml(value) { return String(value).replace(/[&<>"']/g,char=>({"&":"&amp;","<":"&lt;",">":"&gt;","\"":"&quot;","'":"&#39;"}[char])); }

  window.walkItRefreshProfilePhoto = function() {
    if(!bridge || !["Profile","Edit_Profile"].includes(screenName)) return;
    const path=bridge.getProfilePhotoPath();
    if(!path) return;
    document.querySelectorAll('[aria-label="Profile picture"], [role="img"][aria-label="Profile"]').forEach(item=>{
      item.style.backgroundImage=`url("file://${encodeURI(path)}?v=${Date.now()}")`;
      item.style.backgroundSize="cover";
      item.style.backgroundPosition="center";
    });
  };

  function renderRewards() {
    if (!bridge || screenName !== "Rewards") return;
    const canvas=document.querySelector(".wrap > div");
    const content=canvas && Array.from(canvas.children).find(item=>item.style.top==="168px");
    if (!content) return;
    const dark=fileName.endsWith("_Dark");
    const foreground=dark ? "#f2f2f2" : "#1a1a1a";
    const muted=dark ? "#b9b9b9" : "#626262";
    const card=dark ? "#242424" : "#fff";
    let data;
    try { data=JSON.parse(bridge.getRewards()); }
    catch (_) { data={error:"Rewards are unavailable right now."}; }
    content.style.cssText += ";bottom:72px;overflow-y:auto;padding-bottom:18px;scrollbar-width:none";
    if (data.error) {
      content.innerHTML=`<div style="padding:20px;border-radius:16px;background:${card};color:${foreground}">${escapeHtml(data.error)}</div>`;
      return;
    }
    const reports=Math.max(0,Number(data.reports)||0);
    const points=Math.max(0,Number(data.points)||0);
    const tiers=[{name:"Bronze",start:0},{name:"Silver",start:50},{name:"Gold",start:100},{name:"Platinum",start:200}];
    const tier=tiers.filter(item=>points>=item.start).pop();
    const next=tiers[tiers.indexOf(tier)+1];
    const progress=next ? Math.min(100,Math.round((points-tier.start)/(next.start-tier.start)*100)) : 100;
    const badges=[
      {name:"First Report",goal:"Submit 1 report",earned:reports>=1,progress:`${Math.min(reports,1)} / 1`},
      {name:"Eagle Eye",goal:"Submit 10 reports",earned:reports>=10,progress:`${Math.min(reports,10)} / 10`},
      {name:"Guardian",goal:"Submit 50 reports",earned:reports>=50,progress:`${Math.min(reports,50)} / 50`},
      {name:"Verifier",goal:"Route confirmations",upcoming:true},
      {name:"Detour Pro",goal:"Detour tracking",upcoming:true},
      {name:"Voice",goal:"Forum posts",upcoming:true}
    ];
    const badgeHtml=badges.map(badge=>`<div style="min-width:0;padding:12px 5px;border:1px solid ${dark?'#444':'#e3e3e3'};border-radius:16px;text-align:center;background:${card};color:${foreground};opacity:${badge.earned?1:0.65}"><div style="width:44px;height:44px;margin:0 auto 7px;border-radius:50%;background:${badge.earned?'#5fbe2a':dark?'#555':'#ddd'};display:grid;place-items:center"><img src="../images/pin.png" alt="" style="width:24px;height:24px;filter:brightness(0) invert(1)"></div><div style="font-size:12px;font-weight:700">${badge.name}</div><div style="font-size:10px;color:${muted};margin-top:3px">${badge.goal}</div><div style="font-size:10px;color:${muted};margin-top:5px">${badge.upcoming?'Coming soon':badge.earned?'Earned':badge.progress}</div></div>`).join("");
    content.innerHTML=`<div style="padding:16px;border-radius:18px;background:#2b2b2b;color:#fff"><div style="font-size:12px;color:#c8c8c8">Current title</div><div style="font-size:22px;font-weight:700">${tier.name}</div><div style="margin-top:10px;height:8px;border-radius:4px;background:#555"><div style="width:${progress}%;height:8px;border-radius:4px;background:#5fbe2a"></div></div><div style="font-size:12px;color:#c8c8c8;margin-top:7px">${next?`${points} points · ${next.start-points} to ${next.name}`:`${points} points · highest title reached`}</div></div><h2 style="font-size:15px;margin:18px 0 10px;color:${foreground}">Badges</h2><div style="display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:9px">${badgeHtml}</div><h2 style="font-size:15px;margin:18px 0 8px;color:${foreground}">Ways to earn</h2><p style="font-size:12px;line-height:1.5;color:${muted};margin:0">Submit a map report to earn 10 points. Your badges update when the report reaches the shared leaderboard.</p>`;
  }

  const forum={posts:[],selected:null,feed:null,mode:"feed",dark:fileName.endsWith("_Dark")};
  const friends={list:null,dark:fileName.endsWith("_Dark")};
  function renderFriends() {
    if(!bridge || screenName!=="Friends") return;
    const canvas=document.querySelector(".wrap > div");
    const search=Array.from(canvas.children).find(item=>item.style.top==="164px");
    friends.list=Array.from(canvas.children).find(item=>item.style.top==="224px");
    if(!search || !friends.list) return;
    search.innerHTML=`<input id="friend-username" aria-label="Friend username" placeholder="Add friend by username" autocapitalize="none" style="min-width:0;flex:1;border:0;outline:0;background:transparent;color:inherit;font:14px Inter,sans-serif"><button id="friend-add" style="height:38px;padding:0 18px;border-radius:19px;border:0;background:#5fbe2a;color:#10300a;font-size:13px;font-weight:700">Add</button>`;
    friends.list.style.bottom="84px"; friends.list.style.overflowY="auto";
    friends.list.innerHTML='<div style="padding:18px;opacity:.7">Loading friends…</div>';
    bridge.loadFriends();
  }
  window.walkItFriends=function(payload) {
    if(!friends.list) return;
    let rows; try { rows=JSON.parse(payload); } catch (_) { rows={error:"Could not load friends."}; }
    if(rows.error) { friends.list.textContent=rows.error.includes("friendships")?"Friends need the database update before they can be used.":rows.error; return; }
    const self=bridge.getCurrentUserId();
    const groups=[
      ["Requests to respond to",rows.filter(item=>item.status==="pending" && item.addressee_id===self)],
      ["Requests sent",rows.filter(item=>item.status==="pending" && item.requester_id===self)],
      ["Friends",rows.filter(item=>item.status==="accepted")]
    ];
    friends.list.innerHTML=groups.map(([heading,items])=>`<div style="font-size:13px;font-weight:700;opacity:.75;margin:14px 0 6px">${heading} · ${items.length}</div>${items.length?items.map(item=>{
      const other=item.requester_id===self?item.addressee:item.requester;
      const name=escapeHtml(other?.display_name||other?.username||"Walker");
      const username=escapeHtml(other?.username||"");
      const actions=item.status==="accepted"?`<button data-friend-action="remove" data-friend-id="${item.id}">Remove</button>`:item.addressee_id===self?`<button data-friend-action="accept" data-friend-id="${item.id}">Accept</button><button data-friend-action="remove" data-friend-id="${item.id}">Decline</button>`:`<button data-friend-action="remove" data-friend-id="${item.id}">Cancel</button>`;
      return `<div style="display:flex;align-items:center;gap:10px;padding:10px 0;border-bottom:1px solid ${friends.dark?'#444':'#eee'}"><div style="width:42px;height:42px;flex:none;border-radius:50%;background:#5fbe2a;color:#fff;display:grid;place-items:center;font-weight:700">${name.slice(0,1).toUpperCase()}</div><div style="flex:1;min-width:0"><div style="font-size:14px;font-weight:700;overflow:hidden;text-overflow:ellipsis">${name}</div><div style="font-size:12px;opacity:.65">@${username}</div></div><div style="display:flex;gap:4px;flex-wrap:wrap;justify-content:flex-end">${actions}</div></div>`;
    }).join(""):'<div style="font-size:12px;opacity:.6;padding:8px 0">None yet</div>'}`).join("");
    friends.list.querySelectorAll('[data-friend-action]').forEach(button=>button.style.cssText+=`;min-height:38px;padding:0 10px;border-radius:19px;border:1px solid ${friends.dark?'#555':'#ddd'};background:${button.dataset.friendAction==='accept'?'#5fbe2a':friends.dark?'#242424':'#fff'};color:${button.dataset.friendAction==='accept'?'#10300a':'inherit'};font-size:12px`);
  };
  window.walkItFriendAction=function(payload) {
    const result=JSON.parse(payload);
    const add=document.getElementById("friend-add"); if(add) add.disabled=false;
    if(result.error) { bridge.showMessage(result.error); return; }
    const field=document.getElementById("friend-username"); if(field) field.value="";
    bridge.loadFriends();
  };
  function showLocalUpdates() {
    if(!forum.feed) return;
    localStorage.setItem("walkit-community-tab","updates");
    forum.mode="updates";
    forum.feed.style.top="164px";
    const create=Array.from(document.querySelectorAll("button")).find(item=>labelFor(item).toLowerCase()==="create a forum");
    if(create) create.style.display="none";
    const tabs=Array.from(document.querySelectorAll("button")).filter(item=>["forum","local updates","friends"].includes(labelFor(item).toLowerCase()));
    const selected=tabs.find(item=>labelFor(item).toLowerCase()==="local updates"); if(selected) setSelected(selected,tabs);
    forum.feed.innerHTML='<div style="padding:24px;text-align:center;opacity:.7">Loading local updates…</div>';
    bridge.loadLocalUpdates();
  }
  window.walkItLocalUpdates=function(payload) {
    if(forum.mode!=="updates" || !forum.feed) return;
    let data; try { data=JSON.parse(payload); } catch (_) { data={error:"Could not load updates."}; }
    if(data.error) { forum.feed.textContent=data.error; return; }
    const rows=data.reports||[];
    forum.feed.innerHTML=`<div style="font-size:12px;opacity:.7;margin-bottom:12px">${data.nearby?'Reports within 10 km of your location':'Recent reports across the Philippines'}</div>`+
      (rows.length?rows.map(item=>`<div style="padding:14px;margin-bottom:12px;border:1px solid ${forum.dark?'#444':'#e3e3e3'};border-radius:18px;background:${forum.dark?'#242424':'#fff'}"><div style="font-size:12px;opacity:.65">${escapeHtml(new Date(item.created_at).toLocaleString())}${item.distance_m!=null?' · '+(item.distance_m/1000).toFixed(1)+' km away':''}</div><div style="font-size:15px;font-weight:700;margin:8px 0">${escapeHtml(item.category||'Map report')}</div><div style="font-size:13px;line-height:1.45">${escapeHtml(item.description||'')}</div><button data-report-lat="${Number(item.latitude)}" data-report-lon="${Number(item.longitude)}" style="margin-top:12px;border:0;background:none;color:#5fbe2a;font-size:13px;font-weight:700;padding:8px 0">Show on map →</button></div>`).join(""):'<div style="padding:20px;text-align:center;opacity:.7">No reports here yet. Check back later or add one on the map.</div>');
  };
  function forumCard(post) {
    const author=escapeHtml(post.display_name||post.username||"Walker");
    const when=new Date(post.created_at).toLocaleString();
    return `<button data-forum-post="${Number(post.id)}" style="display:block;width:100%;margin:0 0 12px;padding:16px;text-align:left;border:1px solid ${forum.dark?'#444':'#e3e3e3'};border-radius:18px;background:${forum.dark?'#242424':'#fff'};color:${forum.dark?'#f2f2f2':'#1a1a1a'};box-shadow:0 4px 14px #0001"><div style="font-size:12px;opacity:.65">${author} · ${escapeHtml(when)}</div><div style="font-size:16px;font-weight:700;margin-top:9px">${escapeHtml(post.title)}</div><div style="font-size:13px;line-height:1.45;margin-top:6px;white-space:pre-wrap">${escapeHtml(post.body)}</div><div style="font-size:12px;color:#5fbe2a;margin-top:12px">${Number(post.reply_count)||0} replies · Open discussion</div></button>`;
  }
  function renderForumFeed() {
    if(!forum.feed) return;
    forum.mode="feed";
    forum.selected=null;
    forum.feed.innerHTML=forum.posts.length?forum.posts.map(forumCard).join(""):'<div style="padding:24px;text-align:center;opacity:.7">No discussions yet. Start the first one.</div>';
  }
  function renderCommunity() {
    if(!bridge || screenName!=="Community") return;
    const canvas=document.querySelector(".wrap > div");
    forum.feed=canvas && Array.from(canvas.children).find(item=>item.style.top==="228px");
    if(!forum.feed) return;
    forum.mode="feed";
    forum.feed.style.overflowY="auto";
    forum.feed.style.padding="0 16px 16px";
    forum.feed.style.boxSizing="border-box";
    forum.feed.innerHTML='<div style="padding:24px;text-align:center;opacity:.7">Loading discussions…</div>';
    if(localStorage.getItem("walkit-community-tab")==="updates") showLocalUpdates();
    else bridge.loadForumPosts();
  }
  function forumError(message) {
    return /forum_(feed|posts|replies)|schema cache/i.test(message||"")
      ? "The shared forum is being set up. Please try again after the database update."
      : message||"The forum is unavailable right now.";
  }
  function showForumComposer() {
    if(!forum.feed) return;
    forum.mode="compose";
    forum.feed.innerHTML=`<button id="forum-back" style="border:0;background:none;color:#5fbe2a;font-size:14px;font-weight:700;padding:0 0 14px">← Back to forum</button><h2 style="font-size:19px;margin:0 0 16px">Start a discussion</h2><label style="display:block;font-size:12px;font-weight:700;margin-bottom:6px">Title</label><input id="forum-title" maxlength="100" placeholder="What would you like to discuss?" style="width:100%;height:48px;box-sizing:border-box;border:1px solid #bbb;border-radius:14px;padding:0 12px;background:${forum.dark?'#242424':'#fff'};color:inherit;font:14px Inter,sans-serif"><label style="display:block;font-size:12px;font-weight:700;margin:16px 0 6px">Details</label><textarea id="forum-body" maxlength="3000" placeholder="Share details with other walkers" style="width:100%;height:150px;box-sizing:border-box;border:1px solid #bbb;border-radius:14px;padding:12px;background:${forum.dark?'#242424':'#fff'};color:inherit;font:14px Inter,sans-serif;resize:vertical"></textarea><div id="forum-message" role="status" style="min-height:20px;margin-top:8px;color:#b42318;font-size:12px"></div><button id="forum-submit" style="width:100%;height:48px;border:0;border-radius:24px;background:#5fbe2a;color:#10300a;font-weight:700">Post discussion</button>`;
  }
  function showForumThread(post) {
    if(!forum.feed) return;
    forum.mode="thread";
    forum.selected=post;
    forum.feed.innerHTML=`<button id="forum-back" style="border:0;background:none;color:#5fbe2a;font-size:14px;font-weight:700;padding:0 0 14px">← Back to forum</button><h2 style="font-size:19px;margin:0 0 6px">${escapeHtml(post.title)}</h2><div style="font-size:12px;opacity:.65">${escapeHtml(post.display_name||post.username||"Walker")} · ${escapeHtml(new Date(post.created_at).toLocaleString())}</div><p style="font-size:14px;line-height:1.5;white-space:pre-wrap">${escapeHtml(post.body)}</p><h3 style="font-size:15px;margin:24px 0 12px">Replies</h3><div id="forum-replies">Loading replies…</div><textarea id="forum-reply-body" maxlength="1000" placeholder="Write a reply" style="width:100%;height:88px;box-sizing:border-box;border:1px solid #bbb;border-radius:14px;padding:12px;margin-top:16px;background:${forum.dark?'#242424':'#fff'};color:inherit;font:14px Inter,sans-serif;resize:vertical"></textarea><div id="forum-message" role="status" style="min-height:20px;margin-top:8px;color:#b42318;font-size:12px"></div><button id="forum-reply-submit" style="width:100%;height:46px;border:0;border-radius:23px;background:#5fbe2a;color:#10300a;font-weight:700">Post reply</button>`;
    bridge.loadForumReplies(Number(post.id));
  }
  window.walkItForumFeed=function(payload) {
    let result; try { result=JSON.parse(payload); } catch (_) { result={error:"Could not load discussions."}; }
    if(result.error) { if(forum.feed && forum.mode==="feed") forum.feed.innerHTML=`<div style="padding:20px;line-height:1.5">${escapeHtml(forumError(result.error))}</div>`; return; }
    forum.posts=Array.isArray(result)?result:[];
    if(forum.mode==="feed") renderForumFeed();
  };
  window.walkItForumReplies=function(payload) {
    const target=document.getElementById("forum-replies"); if(!target) return;
    let result; try { result=JSON.parse(payload); } catch (_) { result={error:"Could not load replies."}; }
    if(result.error) { target.textContent=forumError(result.error); return; }
    target.innerHTML=result.length?result.map(reply=>`<div style="padding:12px;margin-bottom:8px;border-radius:14px;background:${forum.dark?'#242424':'#f3f3f3'}"><b style="font-size:12px">${escapeHtml(reply.display_name||reply.username||"Walker")}</b><span style="font-size:11px;opacity:.6"> · ${escapeHtml(new Date(reply.created_at).toLocaleString())}</span><div style="font-size:13px;line-height:1.45;white-space:pre-wrap;margin-top:6px">${escapeHtml(reply.body)}</div></div>`).join(""):'<div style="font-size:13px;opacity:.65">No replies yet. Add the first one.</div>';
  };
  window.walkItForumPostSaved=function(payload) {
    const result=JSON.parse(payload),button=document.getElementById("forum-submit");
    if(button) button.disabled=false;
    if(result.error) { const message=document.getElementById("forum-message"); if(message) message.textContent=forumError(result.error); return; }
    forum.mode="feed";
    forum.feed.innerHTML='<div style="padding:24px;text-align:center">Loading discussions…</div>';
    bridge.loadForumPosts();
  };
  window.walkItForumReplySaved=function(payload) {
    const result=JSON.parse(payload),button=document.getElementById("forum-reply-submit");
    if(button) button.disabled=false;
    if(result.error) { const message=document.getElementById("forum-message"); if(message) message.textContent=forumError(result.error); return; }
    const field=document.getElementById("forum-reply-body"); if(field) field.value="";
    if(forum.selected) bridge.loadForumReplies(Number(forum.selected.id));
    bridge.loadForumPosts();
  };

  document.addEventListener("click", function (event) {
    if(screenName==="Edit_Profile" && event.target.closest('[aria-label="Profile picture"], img[alt="Change photo"]')) {
      if(bridge) bridge.changeProfilePhoto();
      return;
    }
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
    if(screenName==="Community") {
      if(lower==="local updates") { showLocalUpdates(); return; }
      if(lower==="forum") {
        localStorage.setItem("walkit-community-tab","forum");
        forum.feed.style.top="228px";
        const create=Array.from(document.querySelectorAll("button")).find(item=>labelFor(item).toLowerCase()==="create a forum");
        if(create) create.style.display="flex";
        const tabs=Array.from(document.querySelectorAll("button")).filter(item=>["forum","local updates","friends"].includes(labelFor(item).toLowerCase()));
        setSelected(button,tabs); renderForumFeed(); return;
      }
      if(lower==="friends") { bridge.navigate("friends"); return; }
      const reportButton=button.closest('[data-report-lat]');
      if(reportButton) { bridge.showReportOnMap(Number(reportButton.dataset.reportLat),Number(reportButton.dataset.reportLon)); return; }
      const postId=Number(button.getAttribute("data-forum-post"));
      if(postId>0) { const post=forum.posts.find(item=>Number(item.id)===postId); if(post) showForumThread(post); return; }
      if(button.id==="forum-back") { renderForumFeed(); return; }
      if(button.id==="forum-submit") {
        const title=document.getElementById("forum-title")?.value.trim()||"";
        const body=document.getElementById("forum-body")?.value.trim()||"";
        const message=document.getElementById("forum-message");
        if(title.length<5 || body.length<10) { if(message) message.textContent="Use at least 5 characters in the title and 10 in the details."; return; }
        button.disabled=true; bridge.createForumPost(title,body); return;
      }
      if(button.id==="forum-reply-submit") {
        const body=document.getElementById("forum-reply-body")?.value.trim()||"";
        const message=document.getElementById("forum-message");
        if(!body) { if(message) message.textContent="Write a reply first."; return; }
        button.disabled=true; bridge.createForumReply(Number(forum.selected?.id),body); return;
      }
      if(lower==="forum") { renderForumFeed(); return; }
    }
    if(screenName==="Friends") {
      if(lower==="forum" || lower==="local updates") {
        localStorage.setItem("walkit-community-tab",lower==="forum"?"forum":"updates");
        bridge.navigate("community"); return;
      }
      if(button.id==="friend-add") {
        const name=document.getElementById("friend-username")?.value.trim()||"";
        if(!name) { bridge.showMessage("Enter a username to add a friend."); return; }
        button.disabled=true; bridge.sendFriendRequest(name); return;
      }
      if(button.dataset.friendAction) { button.disabled=true; bridge.updateFriendRequest(button.dataset.friendId,button.dataset.friendAction); return; }
    }
    if(button.id==="open-signup") { bridge.navigate("signup"); return; }
    if(button.id==="open-login") { bridge.navigate("login"); return; }
    if(button.id==="login-submit") {
      const email=document.getElementById("account-email").value.trim();
      const password=document.getElementById("account-password").value;
      if(!email || !password) { window.walkItAccountMessage("Enter your email and password."); return; }
      button.disabled=true; bridge.login(email,password); return;
    }
    if(button.id==="signup-submit") {
      const password=document.getElementById("account-password").value;
      const email=document.getElementById("account-email").value.trim();
      const username=document.getElementById("account-username").value.trim();
      const name=document.getElementById("account-name").value.trim();
      if(!email || !username || !name || !password) { window.walkItAccountMessage("Complete every field to create your account."); return; }
      if(password!==document.getElementById("account-confirm").value) { window.walkItAccountMessage("Passwords do not match."); return; }
      button.disabled=true; bridge.register(email,username,password,name); return;
    }
    if(button.id==="profile-save") { bridge.saveProfile(document.getElementById("profile-name").value,document.getElementById("profile-bio").value); return; }
    if(button.id==="profile-logout") { bridge.logout(); return; }
    if(button.id==="profile-leaderboard") { bridge.navigate("leaderboard"); return; }
    if(button.id==="profile-map") { bridge.navigate("map"); return; }
    if(button.id==="leaderboard-back") { bridge.navigate("rewards"); return; }
    if(button.id==="show-account-password") {
      const show=document.getElementById("account-password").type==="password";
      ["account-password","account-confirm"].forEach(id=>{ const field=document.getElementById(id); if(field) field.type=show?"text":"password"; });
      button.textContent=show?"Hide":"Show"; return;
    }
    if(screenName==="Login" && lower==="sign up") { bridge.navigate("signup"); return; }
    if(screenName==="Signup" && lower==="log in") { bridge.navigate("login"); return; }
    if(lower==="cancel" && screenName==="Edit_Profile") { bridge.navigate("profile"); return; }
    if(lower==="forgot password?") { bridge.showMessage("Password reset is being prepared. Contact the team if you need access restored."); return; }
    if(lower==="this week") { bridge.showMessage("Weekly rankings are not available yet."); return; }
    const providerLabel=button.getAttribute("aria-label")?.toLowerCase() || "";
    if(providerLabel.includes("sign in with google") || providerLabel.includes("sign up with google")) { bridge.signInWithProvider("google"); return; }
    if(providerLabel.includes("sign in with apple") || providerLabel.includes("sign up with apple")) { bridge.signInWithProvider("apple"); return; }
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
    if (screenName === "Report" && lower === "retake") { if (bridge) bridge.retakeReportPhoto(); return; }
    if (lower === "report an issue" || lower === "take photo") {
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
        if (bridge) bridge.showMessage("Enter a start and destination place or address.");
        return;
      }
      document.getElementById("route-status").textContent = "Finding a walking route…";
      if (bridge) bridge.planRoute(origin.value.trim(),destination.value.trim());
      return;
    }
    if (lower === "find detour") { if (bridge) bridge.planDetour(); return; }
    if (screenName === "Routes" && ["wheelchair access", "fewer stairs", "smooth path", "well lit"].includes(lower)) {
      if (bridge) bridge.showMessage("This route data cannot verify accessibility, stairs, surfaces, or lighting yet.");
      return;
    }
    if (lower === "done" || lower === "back to map" || lower === "home") { if (bridge) bridge.navigate("map"); return; }
    if (lower === "create a forum") { showForumComposer(); return; }
    if (lower === "save changes") { if (bridge) bridge.showMessage("Profile editing is a visual prototype in this build."); return; }
    if (lower === "change photo") { if (bridge) bridge.changeProfilePhoto(); return; }
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

  document.addEventListener("change",function(event){
    if(event.target?.id==="show-account-password") {
      ["account-password","account-confirm"].forEach(id=>{
        const field=document.getElementById(id);
        if(field) field.type=event.target.checked ? "text" : "password";
      });
    }
  });

  window.addEventListener("resize", sizeToDevice);
  window.walkItRefreshDraft = hydrateReport;
  window.walkItRefreshPreview = hydratePreview;
  window.walkItClearDraft = clearDraft;
  window.walkItAccountMessage = function(message) {
    const view=document.getElementById("account-message");
    if(view) view.textContent=message;
    document.querySelectorAll("#login-submit,#signup-submit").forEach(button=>button.disabled=false);
  };
  window.walkItRouteReady = function(summary,warning) {
    const status=document.getElementById("route-status");
    const details=document.getElementById("route-summary");
    if(status) status.textContent=warning || "Walking route is ready on the map";
    if(details) details.textContent=summary;
    const detour=document.getElementById("route-detour");
    if(detour) detour.style.display=warning ? "flex" : "none";
  };
  window.walkItRouteError = function(message) {
    const status=document.getElementById("route-status");
    if(status) status.textContent=message;
    const detour=document.getElementById("route-detour");
    if(detour) detour.style.display="none";
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
  renderAccountPage();
  renderRewards();
  renderCommunity();
  renderFriends();
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
