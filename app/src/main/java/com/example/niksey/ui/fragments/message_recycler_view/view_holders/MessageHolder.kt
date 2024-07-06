package com.example.niksey.ui.fragments.message_recycler_view.view_holders

import com.example.niksey.ui.fragments.message_recycler_view.views.MessageView

interface MessageHolder {
    fun drawMessage(view: MessageView)
    fun onAttach(view: MessageView)
    fun onDetach()
}