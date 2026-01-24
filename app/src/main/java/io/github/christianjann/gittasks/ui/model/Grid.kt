package io.github.christianjann.gittasks.ui.model

import androidx.room.Embedded
import androidx.room.Ignore
import io.github.christianjann.gittasks.MyApp
import io.github.christianjann.gittasks.R
import io.github.christianjann.gittasks.data.room.Note

enum class SortOrder {
    AZ,
    ZA,
    MostRecent,
    Oldest;

    override fun toString(): String {
        val res = when (this) {
            AZ -> R.string.az_sort_order
            ZA -> R.string.za_sort_order
            MostRecent -> R.string.most_recent_sort_order
            Oldest -> R.string.oldest_sort_order
        }
        return MyApp.appModule.uiHelper.getString(res)
    }
}

enum class NoteMinWidth(val size: Int) {
    C100(100),
    Default(150),
    C250(250),
    C300(300),
    C350(350),
    C400(400),
    C500(500),
    C600(600);

    override fun toString(): String = this.size.toString()
}

enum class NoteViewType {
    Grid,
    List,
    Due,
}

enum class TagDisplayMode {
    None,
    ListOnly,
    GridOnly,
    Both;

    override fun toString(): String {
        val res = when (this) {
            None -> R.string.tag_display_none
            ListOnly -> R.string.tag_display_list_only
            GridOnly -> R.string.tag_display_grid_only
            Both -> R.string.tag_display_both
        }
        return MyApp.appModule.uiHelper.getString(res)
    }
}

data class GridNote(
    @Embedded
    val note: Note,
    val isUnique: Boolean,
    val selected: Boolean = false,
    @Ignore
    val completed: Boolean? = null,
    @Ignore
    val dueDate: java.time.LocalDateTime? = null,
) {
    // Secondary constructor for Room - it can't handle @Ignore fields in primary constructor
    constructor(note: Note, isUnique: Boolean, selected: Boolean) : this(
        note = note,
        isUnique = isUnique,
        selected = selected,
        completed = null,
        dueDate = null
    )
}
