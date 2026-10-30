-- Read-only IR code database shipped inside the app (assets/irdb.sqlite).
-- Timing blobs are unsigned LEB128 varints: microsecond durations, alternating
-- mark/space, starting with a mark — ready for ConsumerIrManager.transmit().

CREATE TABLE meta (key TEXT PRIMARY KEY, value TEXT NOT NULL);

CREATE TABLE sources (
    id INTEGER PRIMARY KEY,
    key TEXT NOT NULL UNIQUE,
    name TEXT NOT NULL,
    url TEXT NOT NULL,
    license TEXT NOT NULL,
    revision TEXT NOT NULL
);

CREATE TABLE categories (
    id INTEGER PRIMARY KEY,
    key TEXT NOT NULL UNIQUE,          -- tv, ac, fan, ...
    name_vi TEXT NOT NULL,
    name_en TEXT NOT NULL,
    sort INTEGER NOT NULL
);

CREATE TABLE brands (
    id INTEGER PRIMARY KEY,
    name TEXT NOT NULL,                -- display name
    norm TEXT NOT NULL UNIQUE          -- lower-case alphanumeric key
);

CREATE TABLE codesets (
    id INTEGER PRIMARY KEY,
    brand_id INTEGER NOT NULL REFERENCES brands(id),
    category_id INTEGER NOT NULL REFERENCES categories(id),
    name TEXT NOT NULL,                -- model / remote name from the source
    source_id INTEGER NOT NULL REFERENCES sources(id),
    source_path TEXT NOT NULL,
    category_inferred INTEGER NOT NULL, -- 1 = category guessed from button names
    button_count INTEGER NOT NULL,
    rank REAL NOT NULL                 -- higher = try first in the matching wizard
);
CREATE INDEX codesets_cat_brand ON codesets(category_id, brand_id, rank DESC);

CREATE TABLE buttons (
    id INTEGER PRIMARY KEY,
    codeset_id INTEGER NOT NULL REFERENCES codesets(id),
    func_key TEXT,                     -- normalized function (POWER, VOL_UP, ...) or NULL
    label TEXT NOT NULL,               -- original button name
    freq INTEGER NOT NULL,             -- carrier frequency, Hz
    pattern BLOB NOT NULL,             -- one short press
    repeat BLOB,                       -- frame repeated while held; NULL = same as pattern
    protocol TEXT,                     -- decoded protocol, NULL for raw captures
    params TEXT
);
CREATE INDEX buttons_codeset ON buttons(codeset_id);

CREATE TABLE category_brands (
    category_id INTEGER NOT NULL,
    brand_id INTEGER NOT NULL,
    codeset_count INTEGER NOT NULL,
    PRIMARY KEY (category_id, brand_id)
) WITHOUT ROWID;
