export function HeroChessBackdrop() {
  return (
    <div className="hero-chess-backdrop" aria-hidden="true">
      <svg className="hero-chess-piece hero-chess-rook" viewBox="0 0 140 200" focusable="false">
        <path d="M22 12h23v22h14V12h22v22h14V12h23v48l-17 18-9 69 16 12v12h11v17H21v-17h11v-12l16-12-9-69-17-18z" />
      </svg>
      <svg className="hero-chess-piece hero-chess-knight" viewBox="0 0 160 220" focusable="false">
        <path d="m59 12 25 13 23 5c25 13 39 39 39 71 0 31-14 55-21 73l9 12v12h10v16H17v-16h11v-12l14-15c7-18 28-38 43-53l-2-22-26 19-26-2-14-19 17-24 13-31 12-8zM63 46l-8 17 17-6z" fillRule="evenodd" />
      </svg>
      <svg className="hero-chess-piece hero-chess-pawn" viewBox="0 0 120 200" focusable="false">
        <circle cx="60" cy="38" r="27" />
        <path d="M36 71h48v14l-12 11c-1 22 7 40 19 56l7 8v13h9v17H13v-17h9v-13l7-8c12-16 20-34 19-56L36 85z" />
      </svg>
    </div>
  )
}
