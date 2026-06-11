package com.example.ez_capstone.ui.components.navigation

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke

/**
 * Canvas-drawn turn arrow. Replaces Unicode emoji in TurnInstructionCard.
 * directionCode follows Kakao Mobility guide type conventions.
 */
@Composable
fun TurnArrowCanvas(
    directionCode: Int,
    color: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val sw = size.width * 0.13f
        val stroke = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val ah = size.width * 0.22f

        when (directionCode) {
            0    -> drawStraight(color, stroke, ah)
            1    -> drawLeftTurn(color, stroke, ah)
            2    -> drawRightTurn(color, stroke, ah)
            3    -> drawUTurn(color, stroke, ah)
            5    -> drawElevated(color, stroke, ah)
            6    -> drawUnderground(color, stroke, ah)
            16   -> drawLeftFork(color, stroke, ah)
            17   -> drawRightFork(color, stroke, ah)
            else -> drawStraight(color, stroke, ah)
        }
    }
}

private fun DrawScope.drawStraight(color: Color, stroke: Stroke, ah: Float) {
    val cx = size.width / 2f
    val top = size.height * 0.12f
    val bottom = size.height * 0.88f
    drawLine(color, Offset(cx, bottom), Offset(cx, top), stroke.width, StrokeCap.Round)
    drawLine(color, Offset(cx, top), Offset(cx - ah, top + ah), stroke.width, StrokeCap.Round)
    drawLine(color, Offset(cx, top), Offset(cx + ah, top + ah), stroke.width, StrokeCap.Round)
}

private fun DrawScope.drawLeftTurn(color: Color, stroke: Stroke, ah: Float) {
    val cx = size.width / 2f
    val bottom = size.height * 0.88f
    val midY = size.height * 0.50f
    val leftX = size.width * 0.18f
    val r = size.width * 0.20f
    val path = Path().apply {
        moveTo(cx, bottom)
        lineTo(cx, midY + r)
        quadraticBezierTo(cx, midY, cx - r, midY)
        lineTo(leftX, midY)
    }
    drawPath(path, color, style = stroke)
    drawLine(color, Offset(leftX, midY), Offset(leftX + ah, midY - ah), stroke.width, StrokeCap.Round)
    drawLine(color, Offset(leftX, midY), Offset(leftX + ah, midY + ah), stroke.width, StrokeCap.Round)
}

private fun DrawScope.drawRightTurn(color: Color, stroke: Stroke, ah: Float) {
    val cx = size.width / 2f
    val bottom = size.height * 0.88f
    val midY = size.height * 0.50f
    val rightX = size.width * 0.82f
    val r = size.width * 0.20f
    val path = Path().apply {
        moveTo(cx, bottom)
        lineTo(cx, midY + r)
        quadraticBezierTo(cx, midY, cx + r, midY)
        lineTo(rightX, midY)
    }
    drawPath(path, color, style = stroke)
    drawLine(color, Offset(rightX, midY), Offset(rightX - ah, midY - ah), stroke.width, StrokeCap.Round)
    drawLine(color, Offset(rightX, midY), Offset(rightX - ah, midY + ah), stroke.width, StrokeCap.Round)
}

private fun DrawScope.drawUTurn(color: Color, stroke: Stroke, ah: Float) {
    val leftX = size.width * 0.28f
    val rightX = size.width * 0.72f
    val topY = size.height * 0.18f
    val bottomY = size.height * 0.88f
    val r = (rightX - leftX) / 2f
    val path = Path().apply {
        moveTo(leftX, bottomY)
        lineTo(leftX, topY + r)
        arcTo(
            rect = Rect(leftX, topY, rightX, topY + r * 2f),
            startAngleDegrees = 180f,
            sweepAngleDegrees = -180f,
            forceMoveTo = false
        )
        lineTo(rightX, bottomY)
    }
    drawPath(path, color, style = stroke)
    drawLine(color, Offset(rightX, bottomY), Offset(rightX - ah, bottomY - ah), stroke.width, StrokeCap.Round)
    drawLine(color, Offset(rightX, bottomY), Offset(rightX + ah, bottomY - ah), stroke.width, StrokeCap.Round)
}

private fun DrawScope.drawElevated(color: Color, stroke: Stroke, ah: Float) {
    drawStraight(color, stroke, ah)
    val cx = size.width / 2f
    val top = size.height * 0.12f
    val arcPath = Path().apply {
        moveTo(cx + ah * 0.6f, top + ah * 0.8f)
        quadraticBezierTo(cx + ah * 1.6f, top, cx + ah * 1.6f, top + ah * 0.8f)
    }
    drawPath(arcPath, color.copy(alpha = 0.55f), style = stroke)
}

private fun DrawScope.drawUnderground(color: Color, stroke: Stroke, ah: Float) {
    drawStraight(color, stroke, ah)
    val cx = size.width / 2f
    val bottom = size.height * 0.88f
    val arcPath = Path().apply {
        moveTo(cx + ah * 0.6f, bottom - ah * 0.8f)
        quadraticBezierTo(cx + ah * 1.6f, bottom, cx + ah * 1.6f, bottom - ah * 0.8f)
    }
    drawPath(arcPath, color.copy(alpha = 0.55f), style = stroke)
}

private fun DrawScope.drawLeftFork(color: Color, stroke: Stroke, ah: Float) {
    val cx = size.width / 2f
    val top = size.height * 0.12f
    val bottom = size.height * 0.88f
    val forkY = size.height * 0.48f
    val forkEndX = size.width * 0.20f
    val forkEndY = size.height * 0.22f
    drawLine(color, Offset(cx, bottom), Offset(cx, top), stroke.width, StrokeCap.Round)
    drawLine(color, Offset(cx, top), Offset(cx - ah, top + ah), stroke.width, StrokeCap.Round)
    drawLine(color, Offset(cx, top), Offset(cx + ah, top + ah), stroke.width, StrokeCap.Round)
    drawLine(color, Offset(cx, forkY), Offset(forkEndX, forkEndY), stroke.width * 0.75f, StrokeCap.Round)
    drawLine(color, Offset(forkEndX, forkEndY), Offset(forkEndX + ah * 0.9f, forkEndY + ah), stroke.width * 0.75f, StrokeCap.Round)
    drawLine(color, Offset(forkEndX, forkEndY), Offset(forkEndX + ah * 1.3f, forkEndY + ah * 0.2f), stroke.width * 0.75f, StrokeCap.Round)
}

private fun DrawScope.drawRightFork(color: Color, stroke: Stroke, ah: Float) {
    val cx = size.width / 2f
    val top = size.height * 0.12f
    val bottom = size.height * 0.88f
    val forkY = size.height * 0.48f
    val forkEndX = size.width * 0.80f
    val forkEndY = size.height * 0.22f
    drawLine(color, Offset(cx, bottom), Offset(cx, top), stroke.width, StrokeCap.Round)
    drawLine(color, Offset(cx, top), Offset(cx - ah, top + ah), stroke.width, StrokeCap.Round)
    drawLine(color, Offset(cx, top), Offset(cx + ah, top + ah), stroke.width, StrokeCap.Round)
    drawLine(color, Offset(cx, forkY), Offset(forkEndX, forkEndY), stroke.width * 0.75f, StrokeCap.Round)
    drawLine(color, Offset(forkEndX, forkEndY), Offset(forkEndX - ah * 0.9f, forkEndY + ah), stroke.width * 0.75f, StrokeCap.Round)
    drawLine(color, Offset(forkEndX, forkEndY), Offset(forkEndX - ah * 1.3f, forkEndY + ah * 0.2f), stroke.width * 0.75f, StrokeCap.Round)
}
