# Android 17 输入框/输入法动画丢失修复

## 1. 现象

在 Android 17（API 37）设备上，聊天输入框不再跟随输入法的上升/下降动画：键盘弹出或收起时，
输入框会瞬间跳到位，或在某次键盘动画被取消后一直停在错误位置。

Eta 的聊天舞台在 `AgentChatBody.kt` 中通过 `Modifier.imePadding()` 给底部输入栏加 IME
padding，并使用 `WindowInsets.ime` 判断键盘是否可见；页面 `MainActivity` 已经调用
`enableEdgeToEdge()`，Manifest 也设置了 `android:windowSoftInputMode="adjustResize"`，
因此不是常见的“没有处理 IME inset”问题。

## 2. 根因

项目实际解析到 `androidx.compose.foundation:foundation-layout:1.12.0`。该版本存在一个
Compose IME inset 缺陷（Google IssueTracker **b/552500419**，回归自 1.4.0）：

- `InsetsListener.onPrepare()` 把内部 `runningAnimation` 置为 `true`；
- `onStart()` 清掉 `prepared`，但没有清 `runningAnimation`；
- 只有 `onEnd()` 会清 `runningAnimation`；
- 如果 IME 动画在 `onStart()` 之后被取消（Android 17 上预测性返回手势与键盘收起动画竞争时
  就可能发生），`onEnd()` 永远不会到达；
- 之后 `onApplyWindowInsets()` 既不走 `prepared` 分支，也不走 `!runningAnimation` 分支，
  于是 `WindowInsets.ime` 在监听器恢复前不再更新。

结果是 `Modifier.imePadding()` 一直使用最后一次的键盘高度：键盘已经收起，输入框仍被顶起；
后续键盘再弹出时也没有逐帧动画，表现为“输入框伴随输入法的上升/下降动画丢失”。该缺陷在单 Activity 且 shell 持续读取 insets 的应用里，这个冻结状态会一直持续到下一次成功的 IME 动画（`onProgress`/`onEnd`）或 `WindowInsetsHolder` 的 0→1 重置；应用层无法可靠感知这两种恢复时机，因此需要下面的看门狗。
`WindowInsets.imeAnimationTarget` 在 `onApplyWindowInsets()` 里位于上述判断之前，所以它仍然
是准确的——这是应用层可以自救的唯一入口。

## 3. 修复

新增 `app/src/main/kotlin/io/github/mangi/eta/ui/insets/SafeImeInsets.kt`：

1. `SafeImeInsets` 同时读取 `WindowInsets.ime`（动画值）和 `WindowInsets.imeAnimationTarget`
   （真实目标值）。
2. `rememberSafeImeInsets()` 用 `snapshotFlow` 监听两者：
   - 两者相等：继续使用系统的动画值，保持与输入法逐帧同步；
   - 两者不相等且连续两个 64ms 窗口（共 128ms）都没有新样本：判定动画值已冻结，切换到本地回退动画；
   - 回退动画以 240ms `FastOutSlowInEasing` 从当前值动画到 `imeAnimationTarget`，
     恢复输入框的上升/下降动画；
   - 一旦某个窗口被判定为冻结，本次窗口生命周期内不再信任冻结的动画值（即使它偶然等于
     目标值），避免下一次键盘弹出时再次瞬跳。
3. `Modifier.imePaddingSafe()` 是 `Modifier.imePadding()` 的 drop-in 替代，消费语义一致。
4. `rememberImeVisible()` 改用 `imeAnimationTarget` 判断键盘可见性，避免 `showEmptySuggestions`
   等 UI 被冻结值影响。

`AgentChatBody.kt` 以及项目内其他使用 `imePadding()` 的页面（模型 Provider 详情、浏览器、
角色编辑、记忆、终端控制台、语音设置等）都已切换到 `imePaddingSafe()`。

128ms 的依据：上游实测从 target 翻转到第一帧 `onProgress` 的空窗为 17–36ms；健康动画每帧
都会发出新样本并取消等待，只有真正冻结的读取才会撑过两个 64ms 窗口。双窗口同时避免单帧抖动误判。

## 4. 测试

- `app/src/test/kotlin/io/github/mangi/eta/ui/insets/SafeImeInsetsTest.kt`
  - 健康状态使用 animated inset；
  - 冻结后使用本地回退值；
  - 冻结后非 bottom 边使用 target。
- 真机验证：在 Android 17 设备上反复用返回手势收起键盘、再点击输入框弹出键盘，确认输入框
  每次都跟随动画，且不会残留键盘高度的空白。
- 该修复不依赖 Android 17 专有 API，API 33+ 均可用；如果未来 Compose 修复了 b/552500419，
  `SafeImeInsets` 可以退役（保留 `imePaddingSafe()` 调用即可）。

## 5. 参考

- Google IssueTracker（b/552500419）：<https://issuetracker.google.com/issues/552500419>
- Android 官方 IME inset 动画文档：<https://developer.android.com/develop/ui/views/layout/sw-keyboard>
- Android 官方 IME inset 动画文档：<https://developer.android.com/develop/ui/views/layout/sw-keyboard>
