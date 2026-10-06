package com.example.jarvis.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.jarvis.model.AssistantState
import com.example.jarvis.model.ExecutionPlan
import com.example.jarvis.model.RiskLevel
import com.example.ui.theme.JarvisAmber
import com.example.ui.theme.JarvisBorderCyan
import com.example.ui.theme.JarvisCrimson
import com.example.ui.theme.JarvisCyan
import com.example.ui.theme.JarvisElevatedCard
import com.example.ui.theme.JarvisEmerald
import com.example.ui.theme.JarvisGlassSurface
import com.example.ui.theme.JarvisIndigo
import com.example.ui.theme.JarvisObsidian
import com.example.ui.theme.JarvisTextPrimary
import com.example.ui.theme.JarvisTextSecondary
import com.example.ui.theme.JarvisViolet

@Composable
fun JarvisOrbHero(
    state: AssistantState,
    statusSubtitle: String,
    partialTranscript: String,
    waveformBars: List<Float>,
    onOrbClick: () -> Unit,
    onStopClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "jarvis_orb_transition")

    val outerRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "outer_rot"
    )

    val innerRotation by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 7500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "inner_rot"
    )

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (state) {
                    AssistantState.LISTENING, AssistantState.EXECUTING, AssistantState.VERIFYING -> 700
                    AssistantState.THINKING, AssistantState.SPEAKING -> 900
                    else -> 1800
                },
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val primaryOrbColor = when (state) {
        AssistantState.IDLE -> JarvisCyan
        AssistantState.LISTENING -> Color(0xFF00F5D4)
        AssistantState.THINKING -> JarvisViolet
        AssistantState.EXECUTING -> JarvisCyan
        AssistantState.VERIFYING -> JarvisAmber
        AssistantState.SPEAKING -> Color(0xFF40C4FF)
        AssistantState.COMPLETED -> JarvisEmerald
        AssistantState.AWAITING_CONFIRMATION, AssistantState.DISAMBIGUATION -> JarvisAmber
        AssistantState.ERROR -> JarvisCrimson
    }

    val secondaryOrbColor = when (state) {
        AssistantState.ERROR -> Color(0xFF880E4F)
        AssistantState.COMPLETED -> Color(0xFF00796B)
        AssistantState.AWAITING_CONFIRMATION, AssistantState.DISAMBIGUATION -> Color(0xFFFF6F00)
        else -> JarvisIndigo
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // State Pill
        Surface(
            color = primaryOrbColor.copy(alpha = 0.14f),
            shape = CircleShape,
            border = BorderStroke(1.dp, primaryOrbColor.copy(alpha = 0.55f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(primaryOrbColor)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = state.statusTitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = primaryOrbColor,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Central Glowing Orb Canvas + Interactive Mic Trigger
        Box(
            modifier = Modifier
                .size(176.dp)
                .clip(CircleShape)
                .clickable { onOrbClick() }
                .semantics { contentDescription = "Activate JARVIS voice assistant orb" }
                .testTag("jarvis_central_orb"),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val minDim = minOf(w, h)
                val center = Offset(w / 2f, h / 2f)
                val baseRadius = (minDim / 2f) * 0.78f

                // Outer atmospheric glow
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            primaryOrbColor.copy(alpha = 0.36f * pulseScale),
                            secondaryOrbColor.copy(alpha = 0.16f),
                            Color.Transparent
                        ),
                        center = center,
                        radius = (minDim / 2f) * 0.98f
                    ),
                    radius = (minDim / 2f) * 0.98f,
                    center = center
                )

                // Outer segmented telemetry ring
                rotate(degrees = outerRotation, pivot = center) {
                    val arcSize = Size(baseRadius * 2f, baseRadius * 2f)
                    val topLeft = Offset(center.x - baseRadius, center.y - baseRadius)
                    for (angle in listOf(0f, 90f, 180f, 270f)) {
                        drawArc(
                            color = primaryOrbColor.copy(alpha = 0.85f),
                            startAngle = angle + 10f,
                            sweepAngle = 55f,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                }

                // Counter-rotating inner reactor ring
                val midRadius = baseRadius * 0.76f
                rotate(degrees = innerRotation, pivot = center) {
                    val arcSize = Size(midRadius * 2f, midRadius * 2f)
                    val topLeft = Offset(center.x - midRadius, center.y - midRadius)
                    for (angle in listOf(30f, 150f, 270f)) {
                        drawArc(
                            color = secondaryOrbColor.copy(alpha = 0.9f),
                            startAngle = angle,
                            sweepAngle = 75f,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                }

                // Core pulsing sphere
                val coreRadius = baseRadius * 0.48f * pulseScale.coerceIn(0.85f, 1.08f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.9f),
                            primaryOrbColor,
                            secondaryOrbColor.copy(alpha = 0.85f)
                        ),
                        center = center,
                        radius = coreRadius
                    ),
                    radius = coreRadius,
                    center = center
                )
            }

            // Center Icon inside Orb
            Icon(
                imageVector = when (state) {
                    AssistantState.LISTENING -> Icons.Default.GraphicEq
                    AssistantState.EXECUTING, AssistantState.VERIFYING -> Icons.Default.Stop
                    else -> Icons.Default.Mic
                },
                contentDescription = "JARVIS Core Microphone",
                tint = JarvisObsidian,
                modifier = Modifier.size(32.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Live 24-Bar Voice Waveform
        Row(
            modifier = Modifier
                .fillMaxWidth(0.75f)
                .height(28.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            waveformBars.forEach { amp ->
                val barFraction = if (state == AssistantState.LISTENING || state == AssistantState.SPEAKING) {
                    amp.coerceIn(0.15f, 1f)
                } else {
                    0.16f
                }
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height((28f * barFraction).dp)
                        .clip(CircleShape)
                        .background(
                            Brush.verticalGradient(
                                listOf(primaryOrbColor, secondaryOrbColor)
                            )
                        )
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Status Subtitle & Transcript
        Text(
            text = if (state == AssistantState.LISTENING && partialTranscript.isNotBlank()) {
                partialTranscript
            } else {
                statusSubtitle
            },
            style = MaterialTheme.typography.titleMedium,
            color = JarvisTextPrimary,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        if (state == AssistantState.LISTENING || state == AssistantState.EXECUTING || state == AssistantState.SPEAKING) {
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedButton(
                onClick = onStopClick,
                border = BorderStroke(1.dp, JarvisCrimson.copy(alpha = 0.7f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = JarvisCrimson),
                modifier = Modifier.testTag("stop_jarvis_action_button")
            ) {
                Icon(Icons.Default.Stop, contentDescription = "Stop", modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Stop / Cancel", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
fun SecurityConfirmationCard(
    plan: ExecutionPlan,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accentColor = if (plan.riskLevel == RiskLevel.HIGH) JarvisCrimson else JarvisAmber
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("security_confirmation_card"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = JarvisElevatedCard),
        border = BorderStroke(1.5.dp, accentColor)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (plan.riskLevel == RiskLevel.HIGH) Icons.Default.WarningAmber else Icons.Default.Security,
                    contentDescription = "Security Confirmation",
                    tint = accentColor,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${plan.riskLevel.displayName} • CONFIRMATION REQUIRED",
                        style = MaterialTheme.typography.labelMedium,
                        color = accentColor,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = plan.intentSummary,
                        style = MaterialTheme.typography.titleMedium,
                        color = JarvisTextPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = plan.confirmationPrompt.ifBlank { "Are you sure you want JARVIS to execute this action?" },
                style = MaterialTheme.typography.bodyLarge,
                color = JarvisTextSecondary
            )

            Spacer(modifier = Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("cancel_confirmation_button"),
                    border = BorderStroke(1.dp, JarvisBorderCyan)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Cancel", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Cancel")
                }
                Button(
                    onClick = onConfirm,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("approve_confirmation_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accentColor,
                        contentColor = JarvisObsidian
                    )
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = "Confirm", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Confirm & Execute", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun DisambiguationCard(
    plan: ExecutionPlan,
    onSelectOption: (String) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("disambiguation_card"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = JarvisElevatedCard),
        border = BorderStroke(1.5.dp, JarvisCyan)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.PersonSearch,
                    contentDescription = "Clarification Needed",
                    tint = JarvisCyan,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "FAIL-SAFE • DO NOT GUESS",
                        style = MaterialTheme.typography.labelMedium,
                        color = JarvisCyan
                    )
                    Text(
                        text = plan.clarificationPrompt,
                        style = MaterialTheme.typography.titleMedium,
                        color = JarvisTextPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            plan.clarificationOptions.forEach { option ->
                Button(
                    onClick = { onSelectOption(option) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .testTag("disambiguation_option_$option"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = JarvisGlassSurface,
                        contentColor = JarvisCyan
                    ),
                    border = BorderStroke(1.dp, JarvisCyan.copy(alpha = 0.5f))
                ) {
                    Text(text = "Select: $option", style = MaterialTheme.typography.labelLarge)
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Cancel")
            }
        }
    }
}
