(function () {
    const hasExplicitZone = value => /(?:Z|[+-]\d{2}:?\d{2})$/i.test(value);

    function parseUtc(value) {
        if (value === null || value === undefined || value === '') return null;
        if (value instanceof Date) return new Date(value.getTime());
        if (typeof value === 'number') return new Date(value);
        let text = String(value).trim();
        if (!text) return null;
        // Backend timestamps are stored/serialized as UTC. MySQL/LocalDateTime values often
        // arrive without a zone suffix, so make that UTC meaning explicit before Date parses it.
        if (!hasExplicitZone(text)) {
            text = text.replace(' ', 'T');
            text += 'Z';
        }
        const date = new Date(text);
        return Number.isNaN(date.getTime()) ? null : date;
    }

    function formatLocal(value, options) {
        const date = parseUtc(value);
        return date ? date.toLocaleString(undefined, options) : '—';
    }

    function formatLocalTime(value, options) {
        const date = parseUtc(value);
        return date ? date.toLocaleTimeString(undefined, options) : '—';
    }

    function localInputToUtcIso(value) {
        if (!value) return null;
        // datetime-local intentionally represents the browser's local wall-clock time.
        const date = new Date(value);
        return Number.isNaN(date.getTime()) ? null : date.toISOString();
    }

    function utcToLocalInput(value) {
        const date = parseUtc(value);
        if (!date) return '';
        const pad = n => String(n).padStart(2, '0');
        return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
    }

    function browserZone() {
        try {
            return Intl.DateTimeFormat().resolvedOptions().timeZone || 'Browser local time';
        } catch (_) {
            return 'Browser local time';
        }
    }

    function browserOffsetLabel() {
        const minutes = -new Date().getTimezoneOffset();
        const sign = minutes >= 0 ? '+' : '-';
        const abs = Math.abs(minutes);
        return `UTC${sign}${String(Math.floor(abs / 60)).padStart(2, '0')}:${String(abs % 60).padStart(2, '0')}`;
    }

    window.CryptoTime = { parseUtc, formatLocal, formatLocalTime, localInputToUtcIso, utcToLocalInput, browserZone, browserOffsetLabel };
})();
