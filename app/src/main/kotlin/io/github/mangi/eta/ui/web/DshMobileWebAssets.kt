package io.github.mangi.eta.ui.web

import io.github.mangi.eta.agent.terminal.DshMobileCss
import org.json.JSONObject

/**
 * DSH 移动端注入脚本集中存放，避免 Activity 与前端实现细节耦合。
 *
 * 脚本只做三件事：安装 Eta 顶栏/抽屉、修正移动端 Enter 行为、把 Eta 主题
 * 状态同步给 DSH。它们都不修改 DSH 的业务状态。
 */
internal object DshMobileWebAssets {
    val shellScript: String = """

(function () {
  function install() {
  if (document.getElementById('eta-mobile-bar')) return true;
  var frame = document.querySelector('.pI_x6G_frame');
  if (!frame) return false;
  document.documentElement.classList.add('eta-dsh-document');
  document.body.classList.add('eta-dsh-page');

  var bar = document.createElement('header');
  bar.id = 'eta-mobile-bar';
  bar.innerHTML =
'<button type="button" class="eta-icon" id="eta-menu" aria-label="打开菜单">' +
  '<svg width="22" height="22" viewBox="0 0 24 24" fill="none" aria-hidden="true"><path d="M4 7h16M4 12h16M4 17h16" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"/></svg>' +
'</button>' +
'<div class="eta-heading"><span class="eta-title">DeepSeek Harness</span><span class="eta-preset-line" id="eta-preset-line"><svg class="eta-preset-icon" width="12" height="12" viewBox="0 0 24 24" fill="none" aria-hidden="true"><path d="M12 3.5 20 8v8l-8 4.5L4 16V8l8-4.5Z" stroke="currentColor" stroke-width="1.5"/><path d="M8 10.5h8M8 14h5" stroke="currentColor" stroke-width="1.4" stroke-linecap="round"/></svg><span class="eta-preset" id="eta-preset"></span></span></div>' +
'<button type="button" class="eta-icon eta-dsh-action" id="eta-jobs" aria-label="后台任务">' +
  '<svg width="20" height="20" viewBox="0 0 24 24" fill="none" aria-hidden="true"><rect x="3.5" y="5" width="17" height="14" rx="3" stroke="currentColor" stroke-width="1.5"/><path d="M7 9l2.5 2.5L7 14M12.5 14h4" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/></svg>' +
  '<span class="eta-badge" id="eta-jobs-count">0</span>' +
'</button>' +
'<button type="button" class="eta-icon eta-dsh-action" id="eta-subagents" aria-label="子智能体">' +
  '<svg width="20" height="20" viewBox="0 0 24 24" fill="none" aria-hidden="true"><circle cx="9" cy="8" r="3" stroke="currentColor" stroke-width="1.5"/><path d="M3.5 19c.4-3 2.6-5 5.5-5s5.1 2 5.5 5M16 8.5a2.5 2.5 0 0 1 0 4.8M17 14.2c2.1.5 3.5 2.2 3.8 4.3" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/></svg>' +
  '<span class="eta-badge" id="eta-subagents-count">0</span>' +
'</button>' +
'<button type="button" class="eta-icon" id="eta-new" aria-label="新建会话">' +
  '<svg width="21" height="21" viewBox="0 0 24 24" fill="none" aria-hidden="true"><path d="M12 5v14M5 12h14" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"/></svg>' +
'</button>' +
'<button type="button" class="eta-icon" id="eta-panel" aria-label="打开右侧边栏">' +
  '<svg width="20" height="20" viewBox="0 0 24 24" fill="none" aria-hidden="true"><rect x="3.5" y="4.5" width="17" height="15" rx="2.5" stroke="currentColor" stroke-width="1.4"/><path d="M15.5 4.5v15" stroke="currentColor" stroke-width="1.4"/></svg>' +
'</button>';
  document.body.appendChild(bar);
  document.body.classList.add('eta-has-bar');

  var scrim = document.createElement('div');
  scrim.id = 'eta-scrim';
  document.body.appendChild(scrim);

  function isDrawerOpen() {
var f = document.querySelector('.pI_x6G_frame');
return !!f && !f.hasAttribute('data-sidebar-collapsed');
  }
  function toggleDrawer(open) {
var f = document.querySelector('.pI_x6G_frame');
if (!f) return;
var currentlyOpen = !f.hasAttribute('data-sidebar-collapsed');
if (open === true && currentlyOpen) return;
if (open === false && !currentlyOpen) return;
var toggle = document.querySelector('.hHd-Xa_toggle');
if (toggle) toggle.click();
  }
  document.getElementById('eta-menu').addEventListener('click', function () { toggleDrawer(); });
  scrim.addEventListener('click', function () { toggleDrawer(false); });
  /* DSH focuses the composer on every newly selected blank Session. A user who
 pressed "new conversation" should land on the empty screen first, not get a
 keyboard. Suppress only the short autofocus window; a deliberate tap later
 still opens the editor. */
  var suppressAutoFocusUntil = 0;
  function isComposer(target) {
return !!target && !!target.closest &&
  !!target.closest('.uV2eYG_card, .uV2eYG_input, [contenteditable="true"]');
  }
  function blurComposer() {
var active = document.activeElement;
if (isComposer(active)) active.blur();
  }
  function armAutoFocusGuard() {
suppressAutoFocusUntil = Date.now() + 900;
window.setTimeout(blurComposer, 0);
window.setTimeout(blurComposer, 120);
window.setTimeout(blurComposer, 360);
  }
  document.addEventListener('focusin', function (event) {
if (Date.now() < suppressAutoFocusUntil && isComposer(event.target)) {
  window.setTimeout(function () { event.target.blur(); }, 0);
}
  }, true);
  document.addEventListener('click', function (event) {
if (!event.target || !event.target.closest) return;
if (event.target.closest('#eta-new, .hHd-Xa_newSession')) armAutoFocusGuard();
  }, true);
  document.getElementById('eta-new').addEventListener('click', function () {
var b = document.querySelector('.hHd-Xa_newSession');
if (b) b.click();
  });
  document.getElementById('eta-panel').addEventListener('click', function () {
var b = document.querySelector('[data-sidebar-right-expand]') ||
        document.querySelector('[data-sidebar-right-toggle]') ||
        document.querySelector('.wSkVaW_headerCorner button') ||
        document.querySelector('[aria-label="打开右侧边栏"]') ||
        document.querySelector('[aria-label="展开右侧栏"]');
if (b) b.click();
  });

  function syncDrawer() {
document.body.classList.toggle('eta-drawer-open', isDrawerOpen());
  }
  new MutationObserver(syncDrawer).observe(frame, {
attributes: true,
attributeFilter: ['data-sidebar-collapsed']
  });
  syncDrawer();

  var titleEl = bar.querySelector('.eta-title');
  function syncTitle() {
    var value = document.title || 'DeepSeek Harness';
    var dash = value.indexOf(' — ');
    titleEl.textContent = dash > 0 ? value.slice(0, dash) : 'DeepSeek Harness';
    scheduleTopbarSync();
  }
  var titleNode = document.querySelector('title');
  if (titleNode) new MutationObserver(syncTitle).observe(titleNode, { childList: true });
  syncTitle();

  var presetEl = document.getElementById('eta-preset');
  var presetLine = document.getElementById('eta-preset-line');
  var jobsButton = document.getElementById('eta-jobs');
  var jobsCount = document.getElementById('eta-jobs-count');
  var subagentsButton = document.getElementById('eta-subagents');
  var subagentsCount = document.getElementById('eta-subagents-count');

  function labelMatches(label, terms) {
for (var i = 0; i < terms.length; i++) {
  if (label.indexOf(terms[i]) !== -1) return true;
}
return false;
  }
  function findHeaderTrigger(selector, terms) {
var direct = document.querySelector(selector);
if (direct) return direct;
var actions = document.querySelector('.wSkVaW_headerActions');
if (actions) {
  var inActions = actions.querySelector(selector);
  if (inActions) return inActions;
}
/* CSS-module class names are not a stable integration surface. The
   localized accessible label is; it also survives a DSH frontend rebuild. */
var buttons = document.querySelectorAll('button[aria-label], [role="button"][aria-label]');
for (var i = 0; i < buttons.length; i++) {
  var label = buttons[i].getAttribute('aria-label') || '';
  if (labelMatches(label, terms)) return buttons[i];
}
return null;
  }
  function syncActionBadge(button, countEl, trigger) {
var label = trigger ? (trigger.getAttribute('aria-label') || '') : '';
var match = label.match(/\d+/);
var count = match ? match[0] : '0';
countEl.textContent = count;
button.classList.toggle('eta-action-empty', count === '0');
button.classList.toggle('eta-action-running', /(运行|running)/i.test(label));
  }
  function syncHeaderActions() {
syncActionBadge(jobsButton, jobsCount, findHeaderTrigger('.foD-wG_trigger',
  ['后台任务', 'background job', 'background jobs']));
syncActionBadge(subagentsButton, subagentsCount, findHeaderTrigger('.oXE0lW_trigger',
  ['子智能体', 'subagent', 'subagents']));
  }
  var lastPresetText = '';
  function cleanPresetText(value) {
return value.replace(/\s*\d+\s*个子智能体.*$/i, '')
  .replace(/\s*\d+\s*subagents?.*$/i, '').trim();
  }
  function findPresetText() {
var direct = document.querySelector('.b06baG_label');
if (direct) {
  var directText = cleanPresetText(direct.textContent.trim());
  if (directText) return directText;
}
var scopes = [
  document.querySelector('.wSkVaW_headerActions'),
  document.querySelector('.wSkVaW_header'),
  document.querySelector('[class*="headerActions"]')
];
for (var scopeIndex = 0; scopeIndex < scopes.length; scopeIndex++) {
  var scope = scopes[scopeIndex];
  if (!scope) continue;
  var spans = scope.querySelectorAll('span');
  for (var j = 0; j < spans.length; j++) {
    var actionText = cleanPresetText(spans[j].textContent.trim());
    if (!actionText || actionText.length > 40) continue;
    if (/^(\d+|[\d.]+[KMB]?)$/.test(actionText)) continue;
    if (/后台任务|子智能体|background job|subagent|running|正在运行/i.test(actionText)) continue;
    if (/模式|预设|preset|mode|Coding|写作|研究/i.test(actionText)) return actionText;
  }
}
return '';
  }
  function hasActiveConversation() {
    var session = document.querySelector('[data-conversation-session]');
    if (!session || (session.getAttribute('data-conversation-session') || '').trim() === '') return false;
    /* The first composer is the hero shell; it is still a blank session even
       though a session id already exists. Do not advertise a preset there. */
    if (document.querySelector('[data-phase="hero"], [data-content-phase="hero"]')) return false;
    return true;
  }
  function syncPreset() {
    if (!hasActiveConversation()) {
      presetEl.textContent = '';
      presetEl.hidden = true;
      presetLine.hidden = true;
      return;
    }
    var text = findPresetText();
    if (text !== '') lastPresetText = text;
    var shown = text || lastPresetText;
    presetEl.textContent = shown;
    presetEl.hidden = shown === '';
    presetLine.hidden = shown === '';
  }
  function syncTopbar() {
syncPreset();
syncHeaderActions();
  }
  var topbarSyncScheduled = false;
  function scheduleTopbarSync() {
if (topbarSyncScheduled) return;
topbarSyncScheduled = true;
window.setTimeout(function () {
  topbarSyncScheduled = false;
  syncTopbar();
}, 450);
  }
  /* Streaming messages mutate the whole conversation tree constantly. Observing
     frame-wide here made every token drive header work. The header slot is
     small and changes only when a job/subagent/preset changes, so bind to it
     and keep a low-frequency fallback for SPA route replacement. */
  var headerObserver = null;
  var observedHeader = null;
  function observeHeaderActions() {
    var actions = document.querySelector('.wSkVaW_headerActions');
    if (!actions || actions === observedHeader) return;
    if (headerObserver) headerObserver.disconnect();
    observedHeader = actions;
    headerObserver = new MutationObserver(scheduleTopbarSync);
    headerObserver.observe(actions, {
      childList: true,
      subtree: true,
      characterData: true,
      attributes: true,
      attributeFilter: ['aria-label']
    });
  }
  observeHeaderActions();
  window.setInterval(function () {
    observeHeaderActions();
    syncTopbar();
  }, 800);
  syncTopbar();
  function positionDshMenus() {
    var bar = document.getElementById('eta-mobile-bar');
    if (!bar) return;
    var bottom = bar.getBoundingClientRect().bottom + 4;
    var nodes = document.querySelectorAll(
      '.foD-wG_menu, .oXE0lW_menu, [class*="_menu"], [role="tree"], [role="menu"], ul[aria-label*="后台任务"]'
    );
    for (var i = 0; i < nodes.length; i++) {
      var node = nodes[i];
      var menu = node.closest('.foD-wG_menu, .oXE0lW_menu, [class*="_menu"]') || node;
      if (!menu || menu === bar || bar.contains(menu)) continue;
      menu.style.setProperty('top', bottom + 'px', 'important');
      menu.style.setProperty('left', '12px', 'important');
      menu.style.setProperty('right', '12px', 'important');
      menu.style.setProperty('width', 'auto', 'important');
      menu.style.setProperty('max-width', 'none', 'important');
      menu.style.setProperty('max-height', Math.max(160, window.innerHeight - bottom - 12) + 'px', 'important');
      menu.style.setProperty('box-sizing', 'border-box', 'important');
    }
  }
  function scheduleMenuPosition() {
    window.setTimeout(positionDshMenus, 0);
    window.setTimeout(positionDshMenus, 80);
    window.setTimeout(positionDshMenus, 220);
    window.setTimeout(positionDshMenus, 500);
  }
  jobsButton.addEventListener('click', function () {
var trigger = findHeaderTrigger('.foD-wG_trigger', ['后台任务', 'background job', 'background jobs']);
if (trigger) {
  trigger.click();
  scheduleMenuPosition();
}
  });
  subagentsButton.addEventListener('click', function () {
var trigger = findHeaderTrigger('.oXE0lW_trigger', ['子智能体', 'subagent', 'subagents']);
if (trigger) {
  trigger.click();
  scheduleMenuPosition();
}
  });

  /* DSH's free-text question answer is a controlled textarea. On some Android
 WebViews a draft-store rerender resets its caret to offset 0, so the next
 character lands in front of the previous one (visibly reversed input).
 Capture the native caret before React handles the input and restore it after
 the rerender. Also force auto direction so a CJK IME cannot flip the field. */
  function isDshAnswer(target) {
return !!target && target.tagName === 'TEXTAREA' &&
  !!target.closest('[data-question-key]');
  }
  function markAnswerDirection(target) {
if (isDshAnswer(target) && target.getAttribute('dir') !== 'auto') {
  target.setAttribute('dir', 'auto');
}
  }
  document.addEventListener('focusin', function (event) {
markAnswerDirection(event.target);
  }, true);
  document.addEventListener('input', function (event) {
var target = event.target;
if (!isDshAnswer(target)) return;
markAnswerDirection(target);
var start = target.selectionStart;
var end = target.selectionEnd;
if (start === null || end === null) return;
var restore = function () {
  if (document.activeElement !== target) return;
  try { target.setSelectionRange(start, end); } catch (_) {}
};
if (window.requestAnimationFrame) window.requestAnimationFrame(restore);
window.setTimeout(restore, 0);
  }, true);
  return true;
  }

  if (install()) return;
  var attempts = 0;
  var timer = setInterval(function () {
attempts += 1;
if (install() || attempts > 120) clearInterval(timer);
  }, 250);
})();
    
        """.trimIndent()

    val inputBridge: String = """

        (function() {
          if (window.__etaMobileInputInstalled) return;
          window.__etaMobileInputInstalled = true;
          var mobile = window.matchMedia('(pointer: coarse)').matches || window.innerWidth < 1024;
          if (!mobile) return;
          window.addEventListener('keydown', function(event) {
            if (event.key !== 'Enter' || event.isComposing || event.keyCode === 229 ||
                event.shiftKey || event.ctrlKey || event.metaKey || event.altKey) return;
            var target = event.target;
            if (!target || !(target.tagName === 'TEXTAREA' || target.isContentEditable)) return;
            event.preventDefault();
            event.stopImmediatePropagation();
            if (target.tagName === 'TEXTAREA') {
              var start = target.selectionStart == null ? target.value.length : target.selectionStart;
              var end = target.selectionEnd == null ? start : target.selectionEnd;
              target.setRangeText(String.fromCharCode(10), start, end, 'end');
              target.dispatchEvent(new Event('input', { bubbles: true }));
            } else if (target.closest('[data-lexical-editor="true"], .uV2eYG_input')) {
              // Lexical (DSH) ignores execCommand; drive its beforeinput path instead.
              var lexical = target.closest('[data-lexical-editor="true"], .uV2eYG_input');
              lexical.dispatchEvent(new InputEvent('beforeinput', {
                bubbles: true, cancelable: true, inputType: 'insertLineBreak'
              }));
            } else if (!document.execCommand('insertLineBreak')) {
              document.execCommand('insertText', false, String.fromCharCode(10));
            }
          }, true);
        })();
    
        """.trimIndent()

    val openCodeLayoutBridge: String = """

        (function() {
          if (document.getElementById('eta-opencode-webview')) return;
          var style = document.createElement('style');
          style.id = 'eta-opencode-webview';
          style.textContent = '@media (max-width:767px){' +
            '#root:not(:has(.pI_x6G_frame)){margin-top:0!important}' +
            'header[data-slot=titlebar-v2]{height:36px!important;padding-top:0!important;padding-bottom:0!important}' +
            'main[data-component=connect-server]{padding-top:16px!important}' +
            '.server-connect-content{margin-top:0!important;margin-bottom:0!important}' +
            '}';
          (document.head || document.documentElement).appendChild(style);
        })();
    
        """.trimIndent()

    val layoutBridge: String by lazy {
        val css = JSONObject.quote(DshMobileCss.css)
        """

        (function() {
          if (document.getElementById('eta-mobile-webview')) return;
          function install() {
            if (!document.body) return false;
            // DSH 前端会随版本更换 CSS Module 类名，不能再用旧的 .pI_x6G_frame
            // 当作唯一挂载条件；否则新版本页面会完全跳过移动 CSS。
            var isDsh = document.title.indexOf('DeepSeek Harness') >= 0 ||
              document.querySelector('.pI_x6G_frame') ||
              document.querySelector('[data-dsh-root]');
            if (!isDsh) return false;
            document.documentElement.classList.add('eta-dsh-document');
            document.body.classList.add('eta-dsh-page');
            window.__etaSetDarkMode = function(on) {
              window.__etaDarkForced = !!on;
              function apply() {
                if (window.__etaDarkForced) {
                  document.body.setAttribute('data-ds-dark-theme', '');
                  document.documentElement.style.colorScheme = 'dark';
                  window.__etaDarkForcedOwned = true;
                } else if (window.__etaDarkForcedOwned) {
                  document.body.removeAttribute('data-ds-dark-theme');
                  document.documentElement.style.removeProperty('color-scheme');
                  window.__etaDarkForcedOwned = false;
                }
              }
              apply();
              if (!window.__etaDarkObserver) {
                window.__etaDarkObserver = new MutationObserver(function() {
                  if (window.__etaDarkForced && !document.body.hasAttribute('data-ds-dark-theme')) apply();
                });
                window.__etaDarkObserver.observe(document.body, {
                  attributes: true,
                  attributeFilter: ['data-ds-dark-theme']
                });
              }
            };
            window.__etaSetDarkMode(false);
            var style = document.createElement('style');
            style.id = 'eta-mobile-webview';
            style.textContent = $css;
            (document.head || document.documentElement).appendChild(style);

            // DSH menus are portal-rendered and use generated CSS-module names.
            // Classify only model/subagent/background menus; unrelated menus must
            // keep their native width.
            function classifyDshMenus() {
              var candidates = document.querySelectorAll(
                '[role="menu"], [role="listbox"], [data-radix-popper-content-wrapper] > *, [class*="_list_4ub78_7"], [class*="_menu_6nhg2_547"]'
              );
              for (var i = 0; i < candidates.length; i++) {
                var node = candidates[i];
                var text = (node.innerText || '').slice(0, 2000);
                if (!text || text.length > 1800) continue;
                if (/(模型|思考强度|推理|GLM|Claude|GPT|Gemini|DeepSeek|Model|子智能体|Subagent|后台任务|Background|Jobs)/i.test(text)) {
                  node.classList.add('eta-dsh-menu-sheet');
                }
              }
            }
            function scheduleDshMenuScan() {
              if (window.__etaDshMenuFrame) return;
              window.__etaDshMenuFrame = requestAnimationFrame(function() {
                window.__etaDshMenuFrame = 0;
                classifyDshMenus();
              });
            }
            classifyDshMenus();
            if (!window.__etaDshMenuObserver) {
              window.__etaDshMenuObserver = new MutationObserver(scheduleDshMenuScan);
              window.__etaDshMenuObserver.observe(document.body, {
                childList: true,
                subtree: true
              });
            }
            return true;
          }
          if (install()) return;
          var attempts = 0;
          var timer = setInterval(function() {
            attempts += 1;
            if (install() || attempts > 120) clearInterval(timer);
          }, 250);
        })();
        
        """.trimIndent()
    }
}
