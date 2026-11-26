package com.callscribe.data.db

import android.content.Context

class CallScribeDatabase private constructor(context: Context) {
    private val dao = TranscriptDao(context)

    fun transcriptDao(): TranscriptDao = dao

    companion object {
        @Volatile
        private var INSTANCE: CallScribeDatabase? = null

        fun getInstance(context: Context): CallScribeDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: CallScribeDatabase(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
