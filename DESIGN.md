---
name: Scribble
colors:
  surface: '#fbf8ff'
  surface-dim: '#d6d8f9'
  surface-bright: '#fbf8ff'
  surface-container-lowest: '#ffffff'
  surface-container-low: '#f4f2ff'
  surface-container: '#edecff'
  surface-container-high: '#e6e6ff'
  surface-container-highest: '#dfe0ff'
  on-surface: '#161a32'
  on-surface-variant: '#55423e'
  inverse-surface: '#2b2e48'
  inverse-on-surface: '#f1efff'
  outline: '#88726d'
  outline-variant: '#dbc1ba'
  surface-tint: '#9a442d'
  primary: '#9a442d'
  on-primary: '#ffffff'
  primary-container: '#e07a5f'
  on-primary-container: '#5b1604'
  inverse-primary: '#ffb4a1'
  secondary: '#605f50'
  on-secondary: '#ffffff'
  secondary-container: '#e6e3d0'
  on-secondary-container: '#666556'
  tertiary: '#386753'
  on-tertiary: '#ffffff'
  tertiary-container: '#70a18a'
  on-tertiary-container: '#003725'
  error: '#ba1a1a'
  on-error: '#ffffff'
  error-container: '#ffdad6'
  on-error-container: '#93000a'
  primary-fixed: '#ffdbd2'
  primary-fixed-dim: '#ffb4a1'
  on-primary-fixed: '#3c0800'
  on-primary-fixed-variant: '#7c2e19'
  secondary-fixed: '#e6e3d0'
  secondary-fixed-dim: '#c9c7b5'
  on-secondary-fixed: '#1c1c11'
  on-secondary-fixed-variant: '#48473a'
  tertiary-fixed: '#bbeed4'
  tertiary-fixed-dim: '#9fd1b8'
  on-tertiary-fixed: '#002115'
  on-tertiary-fixed-variant: '#1f4f3c'
  background: '#fbf8ff'
  on-background: '#161a32'
  surface-variant: '#dfe0ff'
typography:
  headline-lg:
    fontFamily: Quicksand
    fontSize: 32px
    fontWeight: '700'
    lineHeight: 40px
    letterSpacing: -0.02em
  headline-lg-mobile:
    fontFamily: Quicksand
    fontSize: 26px
    fontWeight: '700'
    lineHeight: 32px
  headline-md:
    fontFamily: Quicksand
    fontSize: 24px
    fontWeight: '600'
    lineHeight: 32px
  body-lg:
    fontFamily: Quicksand
    fontSize: 18px
    fontWeight: '500'
    lineHeight: 28px
  body-md:
    fontFamily: Quicksand
    fontSize: 16px
    fontWeight: '400'
    lineHeight: 24px
  label-md:
    fontFamily: Quicksand
    fontSize: 14px
    fontWeight: '600'
    lineHeight: 20px
    letterSpacing: 0.01em
  label-sm:
    fontFamily: Quicksand
    fontSize: 12px
    fontWeight: '700'
    lineHeight: 16px
    letterSpacing: 0.03em
rounded:
  sm: 0.5rem
  DEFAULT: 1rem
  md: 1.5rem
  lg: 2rem
  xl: 3rem
  full: 9999px
spacing:
  unit: 4px
  xs: 4px
  sm: 8px
  md: 16px
  lg: 24px
  xl: 40px
  xxl: 64px
  gutter: 20px
  margin-mobile: 16px
  margin-desktop: 48px
---

## Brand & Style
The design system is built to bridge the gap between digital convenience and the tactile warmth of physical stationery. It targets users seeking a more expressive, personal way to communicate, moving away from the clinical feel of traditional messaging apps. 

The aesthetic is **Tactile / Skeuomorphic-lite**, characterized by soft paper textures, organic shadows, and "imperfect" geometry that mimics the hand-cut nature of sticky notes and scrapbooks. The interface should feel like a shared desk surface—welcoming, casual, and highly personal. High-quality whitespace and a limited, earthy palette ensure that the user's handwritten content remains the focal point.

## Colors
The palette is rooted in organic, earthy tones to evoke the feeling of natural paper and ink.

- **Primary (Terracotta):** Used for key actions, brand moments, and primary interactive states. It provides a warm, energetic contrast to the neutral base.
- **Secondary (Ivory):** The primary surface color for cards, notes, and containers. It differentiates "paper" elements from the background.
- **Neutral (Deep Charcoal):** Reserved for UI chrome, iconography, and high-legibility text. It is a soft blue-grey that feels more natural than pure black.
- **Background (Cream):** A warm off-white used for the main application canvas to reduce eye strain and reinforce the notebook metaphor.
- **Accent (Sage):** A tertiary green used sparingly for secondary success states or to denote "fresh" or "new" annotations.

## Typography
This design system utilizes **Quicksand** for all UI elements. Its rounded terminals and open counters complement the "handwritten" nature of the app's core content without competing for attention.

- **Headings:** Use Bold or Semi-Bold weights with slightly tighter letter spacing for a compact, friendly feel.
- **Body:** Use Medium or Regular weights with generous line heights to maintain a "breathable" and casual reading experience.
- **Labels:** Use Semi-Bold or Bold weights in all-caps or title case for navigation and metadata to ensure distinction from body text.

## Layout & Spacing
The layout philosophy is **Loose & Organic**. Avoid rigid, edge-to-edge grids. Instead, use a "Canvas" model where elements are placed with generous margins, mimicking items scattered on a physical desk.

- **Grid:** Use a 12-column fluid grid for desktop, but prioritize "center-stacking" for messaging feeds. 
- **Margins:** Large outer margins (24px+) prevent the UI from feeling cramped.
- **Rhythm:** Spacing follows a 4px base unit, but larger jumps (e.g., from 24px to 40px) are encouraged to create distinct "clusters" of information.
- **Adaptive Strategy:** On mobile, components should take up most of the width but retain at least 16px of the background "Cream" color on all sides to maintain the "floating paper" effect.

## Elevation & Depth
Depth is created through **Ambient Shadows** and **Tonal Layering**, simulating the way paper stacks on a surface.

- **Level 0 (Background):** The Cream surface (#FDFBF7).
- **Level 1 (The Desk):** Soft Ivory surfaces (#F4F1DE) with a very subtle, diffused shadow (10% opacity, 4px blur, 2px Y-offset).
- **Level 2 (The Note):** Active "sticky notes" or cards. Use a more pronounced shadow (15% opacity, 12px blur, 6px Y-offset) to make them feel liftable.
- **Level 3 (Interactions):** FABs and active overlays. Use a deep, warm-tinted shadow (#3D405B at 20% opacity) with a larger offset to imply significant elevation.
- **Edge Treatment:** Avoid sharp borders. Use thin, low-contrast inner strokes (1px, 5% opacity dark blue) to define edges on light surfaces.

## Shapes
The shape language is dominated by **extreme roundedness** and **organic asymmetry**.

- **Cards & Containers:** Use a minimum radius of 24px. For a more tactile feel, apply a slight "wobble" (non-uniform corner radii like 24px, 28px, 22px, 26px) where the platform allows.
- **Floating Action Buttons (FABs):** Use organic "blob" shapes rather than perfect circles. These should feel like hand-drawn ink spots or smooth river stones.
- **Torn Edges:** For card bottoms or list separators, use a masked "torn paper" effect (a jagged, irregular path) to reinforce the analog theme.

## Components
- **The "Scribble" Note (Card):** The primary container. Always use the Ivory background. One corner should have a subtle "dog-ear" fold or a slight rotation (1-2 degrees) to make it look manually placed.
- **Primary Button:** Large, pill-shaped, and Terracotta. Use a bold Quicksand weight for the label. On press, the button should "sink" (reduce shadow and scale slightly).
- **Floating Action Button (FAB):** The main "Compose" action. This should be an irregular organic shape in Terracotta with a high-contrast charcoal icon.
- **Input Fields:** Styled as "underlined" rows, mimicking lined notebook paper. The active state should show a subtle hand-drawn highlighter effect behind the cursor.
- **Chips/Tags:** Small, soft-rounded shapes that look like washi tape. Use semi-transparent versions of the Secondary and Tertiary colors.
- **Lists:** Instead of dividers, use wide vertical spacing or "torn edge" masks between items.
- **Navigation:** Bottom navigation bar should be a floating "dock" with high roundedness, not attached to the screen edges.