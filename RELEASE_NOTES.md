# WebTools 3.0 — a second typeface, folders, and a pull-down that marks its place

## In this release

- **Two typefaces.** Names, sentences and anything a person wrote stay in the phone's own face.
  Everything *about* them — labels, counts, states, the words on the bar, the numerals down the
  left of a list — is monospaced and tracked wide. One face had to carry all of it by size alone,
  and a row of three facts came out as three sizes of the same thing. Now a numeral beside a name
  reads as an index instead of part of the name.
- **The pull-down marks the picked row instead of filling it.** A white block behind black text
  is the loudest thing this app can draw, and it appeared and vanished under the thumb at every
  row: the menu flashed rather than moved. Rows now keep the page's black, labels sit dim, and the
  row the thumb has reached turns white with a short bar at the left edge. Each row shows its own
  number; the lit one shows LET GO, which is the only instruction the gesture ever needed.
- **Loading leaves the page where it is.** Dimmed, with a two-pixel line filling along the top
  edge. The words stay put, so when it clears your eye is already in the right place.
- **Folders.** Tools filed under the same name show as one row — *Tickets · 3 tools* — that opens
  to its own list, and the folder rises up the shelf when anything in it is opened. Hold a tool,
  tap PUT IN A FOLDER, type a name once and tap it for the rest. A folder is only a name written
  on its tools: there is nothing to create, nothing to rename apart from the tools, and nothing
  left behind when the last one leaves. Ticketmaster, AXS and DICE are starters now, and they
  arrive filed together.
- **Every list is numbered**, Add is three numbered steps, a tool's page opens with a grid of
  label and answer rather than stacked fields, and Settings and Info are one-line facts with
  their state down the right-hand edge.
- **The companion page drops Bring a login.** Firefox's engine signs in to the sites that used to
  need it, and a page that asks for a pasted cURL is a page that asks too much. It has a Folder
  field now instead.

## Known limits

- First launch is slower: the engine starts and the two extensions install, once.
- A page that wants a popup gets nothing. A page that wants location, camera, or notifications
  gets a no.
- Bundled tools (Split, Convert) do not keep their last values between launches on this engine.
