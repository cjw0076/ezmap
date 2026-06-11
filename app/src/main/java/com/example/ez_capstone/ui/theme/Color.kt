package com.example.ez_capstone.ui.theme

import androidx.compose.ui.graphics.Color

// OBSIDIAN DESIGN SYSTEM v2 — Color Palette (ui_flow.md 기준)

// Backgrounds (4-layer depth)
val Background   = Color(0xFF06070B)   // 앱 전체 배경
val Surface      = Color(0xFF0D0F14)   // Surface1: 카드/시트 배경
val SurfaceHigh  = Color(0xFF14161D)   // Surface2: 입력 필드/중첩 카드
val SurfaceTop   = Color(0xFF1A1E28)   // Surface3: 호버/강조
val SurfacePeak  = Color(0xFF212636)   // Surface4: 최상위 요소

// Borders
val Border       = Color(0xFF1A1D27)   // 기본 보더
val BorderActive = Color(0xFF252A38)   // 활성 보더
val BorderFocus  = Color(0xFF3A4566)   // 포커스 보더 (액센트 계열)

// Primary Accent: Blue-Violet
val Accent       = Color(0xFF6C8EFF)   // 주 강조색
val AccentDim    = Color(0xFF4A64CC)   // 강조 어두운
val AccentLight  = Color(0xFF8BA4FF)   // 강조 밝은
val AccentGlow   = Color(0x266C8EFF)   // 글로우 (15% opacity)
val AccentPurple = Color(0xFF8B5CF6)   // 보조 강조 (보라)

// Semantic
val Success      = Color(0xFF34D399)   // 에메랄드
val Warning      = Color(0xFFFBBF24)   // 앰버
val Error        = Color(0xFFF87171)   // 코랄

// Text
val TextPrimary   = Color(0xFFF1F3F8)  // 주 텍스트 (거의 흰색)
val TextSecondary = Color(0xFF6B7394)  // 보조 텍스트 (뮤트드 슬레이트)
val TextDim       = Color(0xFF3E4A66)  // 비활성 텍스트
val TextMuted     = Color(0xFF2A3350)  // 최저 대비 텍스트

// Gradients
val GradientAccent  = listOf(Accent, AccentDim)
val GradientCard    = listOf(SurfaceHigh, SurfaceTop)
val GradientDark    = listOf(Surface, SurfaceHigh)

// Mic FAB States (ui_flow.md §5 MicFAB 상세)
val MicIdleStart    = Accent          // 대기: 액센트 그래디언트
val MicIdleEnd      = AccentPurple    // 대기: #6C8EFF → #8B5CF6
val MicListenStart  = Color(0xFFF87171) // 듣기: 빨간 그래디언트
val MicListenEnd    = Color(0xFFEF4444) // 듣기: #F87171 → #EF4444
val MicProcessStart = AccentPurple    // 처리: 보라 그래디언트
val MicProcessEnd   = Accent          // 처리: #8B5CF6 → #6C8EFF
val MicSpeaking     = Accent          // 응답: 원래 액센트

// Legacy Mic aliases (MicFAB.kt 호환, 4.5.3에서 제거 예정)
val MicIdle       = MicIdleStart
val MicListening  = MicListenStart
val MicProcessing = MicProcessStart

// Navigation HUD
val NavSpeed      = Accent
val NavSpeedOver  = Error
val NavEta        = Warning
val NavDistance   = Success
val NavLaneActive = Accent
val NavLaneInactive = Color(0xFF2A3350)

// Skill Status
val SkillActive   = Success
val SkillInactive = TextDim

// Legacy aliases (compile compat)
val Cyan          = Accent
val Purple        = AccentDim
val Green         = Success
val Amber         = Warning
val Red           = Error
val CardSurface   = SurfaceHigh
val GlassBorder   = Accent
val GlassGlow     = AccentGlow
val AccentStart   = AccentDim
val AccentEnd     = Accent
val Secondary     = Accent
val Alert         = Error
val CardBackground = SurfaceHigh

// SurfaceCard alias (used by GlassCard, HUD components)
val SurfaceCard    = SurfaceHigh

// Navigation HUD color aliases
val HudOverspeed   = Error
val HudSpeed       = Accent
val HudEta         = Warning
val HudDistance    = Success

// Gradient aliases
val GradientCyanPurple = listOf(Accent, AccentDim)
