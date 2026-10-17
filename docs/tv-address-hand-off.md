# TV address hand-off

Typing a server address on a television remote is slow. So the TV shows a code, and a phone sends
the address instead (#323). The TV side is `handoff/AddressHandOffListener.kt`: a tiny HTTP/1.1
listener on the TV's own LAN address. The phone side is `SendAddressActivity`, or any browser.

## What the listener answers

It answers three paths, each under one token:

- `/a/<token>`: the page, and the address a phone posts.
- `/s/<token>`: the TV's status, as JSON.
- `/c/<token>`: sign-in credentials, sealed by a phone.

Any other path, a wrong token included, is a bare 404. It says nothing about what is listening.

## Why it is safe to run

- **One-time token.** The token is about 40 random bits. That is short enough to type. It is minted
  for this listener and shown only in the code on screen. The comparison is constant-time. Forty
  bits is enough because of everything below: the listener is LAN-only, serves one connection at a
  time, takes an address only while the TV waits for one, and does not live long.
- **PIN step.** When the TV also shows a PIN, the token alone does not let a client send an address
  or credentials. A client proves it can see the TV by posting the PIN (#803, #909). The listener
  answers a match with a random cookie (`HandOffClients`) and trusts later requests that carry it.
  A match does not open the code to anyone else. The app keeps no cookies, so it sends the PIN with
  every post. Five wrong PINs lock the code, and `serve` returns so its owner shows a new one (#916).
- **LAN only.** The socket is bound to the TV's private IPv4 address on the active Wi-Fi or Ethernet
  network. It is never bound to every interface.
- **Short-lived.** It accepts an address while the TV is waiting for one: the first, and another
  after one that named no server it could reach. Once an address is in, it only answers status
  pages, and its owner keeps it up through the sign-in. It closes on cancel, a short while after the
  TV reaches Connected, or after the sign-in timeout. A code nobody uses lapses after five minutes.
  The owner then replaces it with a new listener, a new port and a new token.
- **Address only.** It accepts a server address, checked with `isValidBaseUrl`, and nothing else.
  Without a sealed session, the address waits for the TV's user to go on before the TV reads that
  server (#1084). A plain-HTTP public address waits on the opt-in instead (#907). The sign-in that
  follows is unchanged.
- **Bounded.** One connection at a time, each bounded by `HandOffLimits`: request size, header
  count, a read timeout and an overall deadline.

It is plain HTTP because there is no certificate a TV could present that a phone would trust. What
crosses the LAN in the clear is the address the user would otherwise type. Credentials cross it
sealed.

## The page's script

The page has one script (#772). Its content security policy allows it by its hash and nothing else
(`PAGE_SCRIPT_HASH`). It does three things.

- **Keeps the key.** It takes the key from the code's URL fragment, which a browser never sends. It
  keeps the key in the tab's session storage, so it survives the page posting the address and
  following the TV. Then it takes the fragment out of the address bar. A refresh to a URL with a
  fragment is a jump within the page, not a reload, so the page would stop following the TV. And
  the key has no business in the tab's history.
- **Hands the key to the app.** It puts the key into the "Open Seerr" link, so the app gets it
  without it crossing the LAN. A page opened from the typed URL has no key, so the app could not
  send the PIN. The PIN step hides the link there.
- **Seals the sign-in.** On the TV's sign-in step it shows the server's sign-in fields. It seals
  what is typed exactly as the app does (`HandOffKey.seal`): AES-256-GCM under an HKDF-SHA256 key,
  with `HandOffKey.context` as associated data. That context is the token and the address the TV is
  signing in to, which the page shows above the form. Then it follows the TV.

The sealing is written out in the script rather than taken from the browser. A page served over
plain HTTP from a LAN address is not a secure context, so the browser's Web Crypto is not available
to it.
