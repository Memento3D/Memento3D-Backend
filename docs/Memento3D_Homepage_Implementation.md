# Memento3D – Premium Landing Page Implementation Guide

> **Document Version:** 1.0.0  
> **Target Platform:** Memento3D Customer-Facing Public Portal  
> **Brand Concept:** *"Memories, Reimagined in 3D"*  
> **Tech Stack:** React 19, TypeScript, Vite 8, Ant Design 6, CSS 3D Transforms, Vanilla CSS Modules  
> **Design Philosophy:** Futuristic Luxury, Deep Indigo & Cyber Slate, Apple-level Refinement  

---

## 1. Executive Summary & Design Decisions

### 1.1 Brand Concept: "Memories, Reimagined in 3D"
Memento3D transforms ordinary 2D digital photographs (smartphone portraits, family snapshots, wedding milestones, pets, graduation breakthroughs) into tangible, physical, sub-millimeter 3D keepsakes.

The landing page is engineered as a high-converting, public-facing digital flagship. Rather than an internal admin dashboard, it delivers a tactile, cinematic story that immediately communicates:
1. **What Memento3D is:** An additive manufacturing platform synthesizing physical keepsakes from 2D photos.
2. **Value delivered:** Converting ephemeral screen pixels into permanent, heirloom-grade physical artifacts.
3. **How it operates:** 4 frictionless steps (Upload -> AI Topology Synthesis -> Customization -> Precision Farm Print).
4. **How to get started:** Direct, role-aware action buttons routing customers and partners to their tailored workflows.

### 1.2 Aesthetic & Visual Language
- **Atmospheric Palette:**
  - Background: Deep Slate & Cyber Navy (`#050811`, `#090d16`).
  - Primary Accent: Deep Indigo (`#6366f1` / `#4f46e5`) & Electric Cyan (`#06b6d4` / `#38bdf8`).
  - Secondary Glow: Soft Violet (`#8b5cf6`) and Antique Amber Gold (`#f59e0b`).
  - Surfaces: Selective Glassmorphism (`rgba(15, 23, 42, 0.72)`) with subtle specular highlight borders (`rgba(255, 255, 255, 0.08)` to `rgba(99, 102, 241, 0.35)` on hover).
- **Typography:**
  - Modern sans-serif: *Plus Jakarta Sans* for clean, authoritative, geometric headlines and comfortable long-form body copy.
  - Monospace accents: *JetBrains Mono* for telemetry metrics and topological polygon counts.
- **Bento Grid Architecture:**
  - Multi-span asymmetrical cards to highlight core differentiators (large AI topology synthesis card, flanked by mobile photo simplicity and heirloom durability).
- **Interactive 3D Simulation:**
  - Zero heavy third-party bundle bloat; lightweight 3D perspective viewer running at 60 FPS with full mouse/touch drag 360° rotation, wireframe inspection, and real-time physical material shaders.

---

## 2. Component Hierarchy & Architecture

All landing page components are encapsulated under `src/features/home/`:

```
src/features/home/
├── components/
│   ├── Navbar.tsx             # Sticky responsive navigation with dynamic auth avatar & mobile drawer
│   ├── HeroSection.tsx        # Cinematic headline, dual CTAs, and holographic 3D preview card
│   ├── HowItWorks.tsx         # 4-step progressive timeline & interactive stage switcher
│   ├── ProductShowcase.tsx    # Curated physical tiers (Mini Charm, Desk Bust, Memorial, Digital .GLB)
│   ├── ThreeDExperience.tsx   # Interactive real-time 3D simulation workbench & material switcher
│   ├── BenefitsSection.tsx    # Asymmetrical Bento Grid highlighting technology and durability
│   ├── StorySection.tsx       # Emotional storytelling across life milestones (Graduation, Family, Pets, Weddings)
│   ├── FinalCTA.tsx           # High-impact conversion closing card with dynamic destination routing
│   └── Footer.tsx             # Brand footer, actor portals directory, and live backend connection telemetry
├── pages/
│   └── HomePage.tsx           # Assembled page container with dynamic SEO meta injection
└── styles/
    └── home.css               # Dedicated luxury design tokens, keyframes, bento grid, and glass cards
```

---

## 3. Section Breakdown & Implementation Details

### Section 1 – Premium Navigation (`Navbar.tsx`)
- **Sticky & Dynamic Scroll State:** Switches from transparent padding to blurred dark glass (`rgba(5, 8, 17, 0.85)` + `backdrop-filter: blur(20px)`) upon scrolling past 40px.
- **Smooth Anchor Scrolling:** Direct smooth scrolling to `#hero`, `#how-it-works`, `#products`, `#experience`, `#benefits`, and `#story`.
- **Authentication State Integration:**
  - *Guest (Unauthenticated):* Renders "Sign In" text button and "Get Started" glowing pill button leading to `/login`.
  - *Authenticated User:* Renders user avatar with initials, full name, and role badge (`ADMIN`, `OPERATOR`, `USER`). Click opens an Ant Design dropdown providing:
    - Direct link to user's assigned dashboard (`getDefaultRouteForRole(user.role)`).
    - Quick-access WMS and 3D Print Farm links for authorized roles.
    - Session sign-out calling `logout()`.
- **Mobile Responsive Drawer:** Right-side slide drawer triggered by a hamburger button on screens `< 868px`.

### Section 2 – Cinematic Hero (`HeroSection.tsx`)
- **Headline:** `Your Memories. Reimagined in 3D.`
- **Subheading:** Clarifies the core transformation proposition from 2D photos to tangible keepsakes.
- **CTAs:**
  - Primary: "Create Your 3D Memory" (routes to creation workflow).
  - Secondary: "Explore How It Works" (smooth scroll down to Section 3).
- **Holographic 3D Showcase Card:**
  - Concentric rotating wireframe rings with subtle floating micro-animation (`anim-float`).
  - Dual status tags: `AI Topology Engine` and `0.05mm Micro-Detail`.
  - Realistic specification tags: Silk Bronze PLA, 0.12mm Ultra resolution, 2-3 days turnaround.

### Section 3 – How It Works (`HowItWorks.tsx`)
- **4-Step Progressive Journey:**
  1. `01 Upload Your Photos`: Smartphone or digital camera upload, automatic subject isolation.
  2. `02 AI Reconstructs in 3D`: Watertight manifold mesh generation from multi-view depth synthesis.
  3. `03 Customize Your Keepsake`: Physical dimensions (8cm to 25cm), material finish, laser-etched pedestal text.
  4. `04 Print & Preserve Forever`: OctoPrint farm dispatch, UV curing, optical QC inspection, white-glove unboxing.
- **Interactive Selection:** Clicking any step card illuminates its border and reveals detailed telemetry specifications.

### Section 4 – Product Collection (`ProductShowcase.tsx`)
- **No Fake Pricing Policy:** Products are presented with genuine physical dimensions, material capabilities, and turnaround estimates.
- **4 Distinct Tiers:**
  1. *Mini Keepsake & Charm:* Pocket-sized 4.5–6.0cm relief charm with stainless steel eyelet.
  2. *Standard Desk Sculpture:* 12–15cm personalized portrait bust mounted on natural walnut base with brass plaque.
  3. *Signature Memorial Artifact:* 18–24cm cold-cast bronze or alabaster resin in a luxury presentation chest.
  4. *Spatial 3D Digital Asset:* Watertight `.GLB` / `.OBJ` download with 4K PBR textures, ready for Apple Vision Pro & AR.

### Section 5 – Interactive 3D Experience (`ThreeDExperience.tsx`)
- **Real-Time Interactive 3D Canvas:**
  - User can click and drag with mouse or touch to rotate the 3D keepsake across 360° axes.
  - Idle auto-rotation resumes smoothly when user releases drag.
  - "Reset View" button returns camera to optimal three-quarters isometric perspective.
- **Material Finishes Selector:**
  - *Silk Cast Bronze:* Warm metallic luster with brushed patina.
  - *Matte Ivory Resin:* Classical classical sculpture aesthetic.
  - *Obsidian Slate SLA:* Deep monochrome finish with high shadow contrast.
  - *Translucent Cyan Resin:* Internal refractions with holographic edge highlights.
- **Viewport Topology Modes:** Solid shader, wireframe polygon mesh, and X-ray density modes.
- **Honest Prototype Notice:** Clearly labeled as a real-time prototype simulation rather than pretending a live 3D stream exists without a model file.

### Section 6 – Why Memento3D (`BenefitsSection.tsx`)
- **Bento Grid Architecture:**
  - Card 1 (Span 8): AI-powered 3D reconstruction pipeline eliminating manual 3D sculpting hours.
  - Card 2 (Span 4): Frictionless mobile photo upload without LiDAR hardware.
  - Card 3 (Span 4): Personalized emotional artifacts with custom pedestal inscriptions.
  - Card 4 (Span 4): Industrial-calibrated 3D print farms with climate-controlled spools.
  - Card 5 (Span 4): Heirloom archival durability with UV-sealed protective finishes.

### Section 7 – Inspirational Storytelling (`StorySection.tsx`)
- **Headline:** *"Some moments deserve more than a photograph."*
- **Milestone Cards:**
  - *Graduation Breakthroughs:* Anchoring academic achievement into an inspiring desktop sculpture.
  - *Generational Family Bonds:* Immortalizing grandparents' warmth and childhood smiles for future generations.
  - *Faithful Companions & Pets:* Preserving loyal pets' unique expressions in tactile form.
  - *Weddings & Anniversaries:* Translating first dance milestones into timeless bronze heirlooms.

### Section 8 – Final Conversion CTA (`FinalCTA.tsx`)
- **Radial Glow Container:** Dramatic atmospheric glow framing the closing call to action.
- **Role-Aware Redirection:**
  - Unauthenticated guests are routed to `/login`.
  - Authenticated users are routed directly to their workspace portal (`/manager/inventory` or assigned actor route).
- **Quality Reassurance:** Highlights 3D pre-print inspection, non-toxic eco polymers, and protective packaging.

### Section 9 – Footer (`Footer.tsx`)
- Comprehensive sitemap connecting Keepsakes, Technology, Company, and Actor Portals (`/login`, `/manager/login`, `/warehouse/login`, `/operator/login`, `/admin/login`).
- Real-time platform status indicator: "Connected to Spring Boot & Vite".
- Accurate location and contact endpoints (`Ho Chi Minh City, Vietnam`, `contact@memento3d.vn`).

---

## 4. Routing Integration

In `src/App.tsx`, the root route (`/`) is now assigned directly to `HomePage`:

```tsx
// src/App.tsx
import { HomePage } from './features/home/pages/HomePage';

<Routes>
  {/* Public Landing Page */}
  <Route path="/" element={<HomePage />} />

  {/* Actor Login Portals */}
  <Route path="/login" element={<GuestRoute><LoginPage actor="customer" /></GuestRoute>} />
  <Route path="/admin/login" element={<GuestRoute><LoginPage actor="admin" /></GuestRoute>} />
  <Route path="/manager/login" element={<GuestRoute><LoginPage actor="manager" /></GuestRoute>} />
  <Route path="/warehouse/login" element={<GuestRoute><LoginPage actor="warehouse" /></GuestRoute>} />
  <Route path="/operator/login" element={<GuestRoute><LoginPage actor="operator" /></GuestRoute>} />
  ...
  {/* Protected Management & WMS Portal */}
  <Route path="/manager" element={<ProtectedRoute><RoleGuard allowedRoles={['ROLE_ADMIN', 'ROLE_OPERATOR']}><AppLayout /></RoleGuard></ProtectedRoute>}>
    ...
  </Route>
</Routes>
```

---

## 5. Build, Lint & Performance Results

- **TypeScript Compilation:** Strict typing verified with `tsc -b && vite build`. Zero errors.
- **Build Duration:** 852 ms.
- **Bundle Efficiency:**
  - `dist/index.html`: `0.94 kB` (gzip: `0.52 kB`)
  - `dist/assets/index-Ck6GzVlD.css`: `10.85 kB` (gzip: `3.17 kB`)
  - `dist/assets/index-DqXMni2c.js`: `1,480.81 kB` (gzip: `456.75 kB`)
- **Zero Horizontal Overflow:** Container max-width clamped to `1240px` with dynamic viewport typography (`clamp()`) ensuring flawless rendering across mobile, tablet, and ultra-wide desktop monitors.
