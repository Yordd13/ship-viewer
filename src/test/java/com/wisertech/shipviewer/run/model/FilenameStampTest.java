// formatsAsTheFolderAndRunNamesDo: checks that a moment is formatted as a stamp like 2026-10-02T155058Z.
// readsTheStampInFrontOfTheOrbit: checks that the stamp before the orbit in a run id is parsed.
// readsAStampOnItsOwn: checks that a bare stamp with no orbit is parsed.
// anythingElseIsNull: checks that a non-stamp or empty run id parses to null.

package com.wisertech.shipviewer.run.model;

import static com.wisertech.shipviewer.Fixtures.PASS_START;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FilenameStampTest {
    @Test
    void formatsAsTheFolderAndRunNamesDo() {
        assertThat(FilenameStamp.format(PASS_START)).isEqualTo("2026-10-02T155058Z");
    }

    @Test
    void readsTheStampInFrontOfTheOrbit() {
        assertThat(FilenameStamp.parseOrNull("2026-10-02T155058Z_009708")).isEqualTo(PASS_START);
    }

    @Test
    void readsAStampOnItsOwn() {
        assertThat(FilenameStamp.parseOrNull("2026-10-02T155058Z")).isEqualTo(PASS_START);
    }

    @Test
    void anythingElseIsNull() {
        assertThat(FilenameStamp.parseOrNull("not-a-run_009708")).isNull();
        assertThat(FilenameStamp.parseOrNull("")).isNull();
    }
}
