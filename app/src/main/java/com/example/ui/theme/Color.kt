package com.example.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// ==========================================
// CallMate AI — Modern "Lucrative" Design System
// ==========================================

// --- Core Brand Gradients & Accents ---
val BrandViolet = Color(0xFF7C3AED)
val BrandIndigo = Color(0xFF6366F1)
val BrandSky = Color(0xFF0EA5E9)
val BrandCyan = Color(0xFF06B6D4)
val BrandRose = Color(0xFFF43F5E)

// Luminous AI Gradient
val AiGradient = Brush.linearGradient(
    colors = listOf(
        Color(0xFF8B5CF6),
        Color(0xFF6366F1),
        Color(0xFF38BDF8)
    )
)

val AiGradientSubtle = Brush.linearGradient(
    colors = listOf(
        Color(0x338B5CF6),
        Color(0x226366F1),
        Color(0x1A38BDF8)
    )
)

val GlowGradient = Brush.radialGradient(
    colors = listOf(
        Color(0x40818CF8),
        Color(0x006366F1)
    )
)

// --- Communication Channel Accents ---
val CallEmerald = Color(0xFF10B981)
val CallEmeraldLight = Color(0xFF34D399)
val CallEmeraldContainer = Color(0x1A10B981)

val SmsSky = Color(0xFF0284C7)
val SmsSkyLight = Color(0xFF38BDF8)
val SmsSkyContainer = Color(0x1A0284C7)

val WhatsAppMint = Color(0xFF22C55E)
val WhatsAppMintLight = Color(0xFF4ADE80)
val WhatsAppMintContainer = Color(0x1A22C55E)

val ReminderAmber = Color(0xFFF59E0B)
val ReminderAmberLight = Color(0xFFFBBF24)
val ReminderAmberContainer = Color(0x1AF59E0B)

// Status / Alerts
val SuccessGreen = Color(0xFF10B981)
val WarningAmber = Color(0xFFF59E0B)
val ErrorRose = Color(0xFFEF4444)

// Backwards compatibility tokens
val IndigoPrimary = BrandIndigo
val IndigoLight = Color(0xFF818CF8)
val IndigoDark = Color(0xFF4338CA)
val CyanAccent = BrandCyan
val CyanLight = Color(0xFF67E8F9)
val EmeraldSuccess = SuccessGreen
val EmeraldLight = Color(0xFF34D399)
val AmberWarning = WarningAmber
val RoseError = ErrorRose
val CallGreen = CallEmerald
val SmsBlue = SmsSky
val WhatsAppGreen = WhatsAppMint

// --- Premium Dark Theme (Obsidian & Deep Slate) ---
val DarkBackground = Color(0xFF090D16)         // Rich OLED-friendly obsidian
val DarkSurface = Color(0xFF111726)            // Card background
val DarkSurfaceElevated = Color(0xFF1A2234)    // Elevated sheet / dialog / modal
val DarkSurfaceSubtle = Color(0xFF151C2C)      // Nested containers / input fields
val DarkBorder = Color(0xFF243048)             // Crisp modern border
val DarkBorderSubtle = Color(0xFF1E283C)

val DarkTextPrimary = Color(0xFFF8FAFC)        // Slate 50
val DarkTextSecondary = Color(0xFF94A3B8)      // Slate 400
val DarkTextMuted = Color(0xFF64748B)          // Slate 500

// --- Elegant Light Theme (Frost & Clean Ceramic) ---
val LightBackground = Color(0xFFF8FAFC)        // Crisp slate 50
val LightSurface = Color(0xFFFFFFFF)           // Pure white cards
val LightSurfaceElevated = Color(0xFFF1F5F9)   // Slate 100
val LightSurfaceSubtle = Color(0xFFF8FAFC)     // Inset / inputs
val LightBorder = Color(0xFFE2E8F0)            // Slate 200 border
val LightBorderSubtle = Color(0xFFEDF2F7)

val LightTextPrimary = Color(0xFF0F172A)       // Slate 900
val LightTextSecondary = Color(0xFF475569)     // Slate 600
val LightTextMuted = Color(0xFF94A3B8)         // Slate 400

// Legacy aliases
val DarkBg = DarkBackground
val DarkSurfaceVariant = DarkSurfaceSubtle
val LightBg = LightBackground
val LightSurfaceVariant = LightSurfaceElevated
