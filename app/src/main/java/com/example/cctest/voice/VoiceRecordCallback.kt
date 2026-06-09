package com.example.cctest.voice

interface VoiceRecordCallback {
    fun onStart()
    fun onCancel()
    fun onFinish()
}
