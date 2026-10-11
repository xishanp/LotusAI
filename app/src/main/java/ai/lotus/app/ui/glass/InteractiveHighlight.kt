/*
 * 来源：Kyant0/AndroidLiquidGlass（Apache License 2.0）
 *   app/src/commonMain/kotlin/com/kyant/backdrop/catalog/utils/InteractiveHighlight.kt
 *
 * 为什么 vendor：它在仓库的 catalog（示例 App）模块里，不随 backdrop 库发布。
 * 手指位置的跟随高光（AGSL 径向衰减 + Plus 混合），玻璃「被手指捂亮」的来源。
 * Apache 2.0 允许复制与修改，保留本声明即可。
 *
 * 改动：换包名；`modifier` 里删掉「按下时给整块玻璃铺一层 Plus 白」并把径向半径
 * 从 1.5×minDimension 收到 0.75×（详见 modifier 内的注释）。**这是唯一的行为改动。**
 */
package ai.lotus.app.ui.glass

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RadialGradient
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.util.fastCoerceIn
import com.kyant.backdrop.RuntimeShader
import com.kyant.backdrop.asComposeShader
import com.kyant.backdrop.isRuntimeShaderSupported
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class InteractiveHighlight(
    val animationScope: CoroutineScope,
    val position: (size: Size, offset: Offset) -> Offset = { _, offset -> offset }
) {

    private val pressProgressAnimationSpec =
        spring(0.5f, 300f, 0.001f)
    private val positionAnimationSpec =
        spring(0.5f, 300f, Offset.VisibilityThreshold)

    private val pressProgressAnimation =
        Animatable(0f, 0.001f)
    private val positionAnimation =
        Animatable(Offset.Zero, Offset.VectorConverter, Offset.VisibilityThreshold)

    private var startPosition = Offset.Zero
    val pressProgress: Float get() = pressProgressAnimation.value
    val offset: Offset get() = positionAnimation.value - startPosition

    private val shader =
        if (isRuntimeShaderSupported()) {
            RuntimeShader(
                """
uniform float2 size;
layout(color) uniform half4 color;
uniform float radius;
uniform float2 position;

half4 main(float2 coord) {
    float dist = distance(coord, position);
    float intensity = smoothstep(radius, radius * 0.5, dist);
    return color * intensity;
}"""
            )
        } else {
            null
        }

    val modifier: Modifier =
        Modifier.drawWithContent {
            val progress = pressProgressAnimation.value
            if (progress > 0f) {
                if (shader != null) {
                    // ⚠️ NASclaw 第十一轮改动（相对上游只此一处）：
                    // 上游这里先给**整块**玻璃铺一层 `Color.White.copy(0.08f * progress)`
                    // 的 Plus 混色，再叠径向衰减。
                    //
                    // 真机观感：底栏静止时非常通透，一按下整条栏蒙上一层白雾 ——
                    // 用户原话「长按之后原本非常通透的菜单栏变模糊了一点」，
                    // 并且要求「只在它接近的地方发光」。
                    //
                    // 原因是这层全局 Plus 与 `blur(8dp)` 叠加后，等于给背景**又加了一层
                    // 均匀的散射**，通透感就没了；而径向高光本身半径是 `minDimension * 1.5f`
                    // （底栏 64dp 高 → 半径 96dp ≈ 覆盖 1.5 个栏高），本就足够。
                    //
                    // → 删掉全局铺色，只保留跟随玻璃泡的局部径向高光；半径收到 0.75 倍，
                    // 让「发光」真的集中在玻璃附近而不是糊满整栏。
                    shader.apply {
                        val position = position(size, positionAnimation.value)
                        setFloatUniform("size", size.width, size.height)
                        setColorUniform("color", Color.White.copy(0.18f * progress))
                        setFloatUniform("radius", size.minDimension * 0.75f)
                        setFloatUniform(
                            "position",
                            position.x.fastCoerceIn(0f, size.width),
                            position.y.fastCoerceIn(0f, size.height)
                        )
                    }
                    drawRect(
                        ShaderBrush(shader.asComposeShader()),
                        blendMode = BlendMode.Plus
                    )
                } else {
                    // 无 AGSL（API < 33）时的退化路径：同样不能铺满整块，
                    // 否则又回到「按下就整栏发白」。
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color.White.copy(0.18f * progress),
                                Color.Transparent
                            ),
                            center = Offset(
                                position(size, positionAnimation.value).x.coerceIn(0f, size.width),
                                size.height / 2f
                            ),
                            radius = size.minDimension * 0.75f
                        ),
                        radius = size.minDimension * 0.75f,
                        center = Offset(
                            position(size, positionAnimation.value).x.coerceIn(0f, size.width),
                            size.height / 2f
                        ),
                        blendMode = BlendMode.Plus
                    )
                }
            }

            drawContent()
        }

    val gestureModifier: Modifier =
        Modifier.pointerInput(animationScope) {
            inspectDragGestures(
                onDragStart = { down ->
                    startPosition = down.position
                    animationScope.launch {
                        launch { pressProgressAnimation.animateTo(1f, pressProgressAnimationSpec) }
                        launch { positionAnimation.snapTo(startPosition) }
                    }
                },
                onDragEnd = {
                    animationScope.launch {
                        launch { pressProgressAnimation.animateTo(0f, pressProgressAnimationSpec) }
                        launch { positionAnimation.animateTo(startPosition, positionAnimationSpec) }
                    }
                },
                onDragCancel = {
                    animationScope.launch {
                        launch { pressProgressAnimation.animateTo(0f, pressProgressAnimationSpec) }
                        launch { positionAnimation.animateTo(startPosition, positionAnimationSpec) }
                    }
                }
            ) { change, _ ->
                animationScope.launch { positionAnimation.snapTo(change.position) }
            }
        }
}
