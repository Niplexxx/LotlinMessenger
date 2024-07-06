package com.example.niksey.ui.screens.private_messages

import android.Manifest.permission.RECORD_AUDIO
import android.annotation.SuppressLint
import android.content.Intent
import android.text.style.TtsSpan.TYPE_TEXT
import android.widget.AbsListView
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.niksey.R
import com.example.niksey.models.CommonModel
import com.example.niksey.models.UserModel
import com.example.niksey.ui.screens.base_fragment.BaseFragment
import com.example.niksey.utillits.*
import com.google.firebase.database.DatabaseReference
import de.hdodenhof.circleimageview.CircleImageView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import android.net.Uri
import android.view.*
import com.example.niksey.database.*
import com.example.niksey.ui.fragments.message_recycler_view.views.AppViewFactory
import com.example.niksey.ui.screens.main_list.MainListFragment
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.theartofdev.edmodo.cropper.CropImage


class SingleChatFragment(private var contact: CommonModel) :
    BaseFragment(R.layout.fragment_chat) {

    private lateinit var mListenerInfoToolbar: AppValueEventListener
    private lateinit var mReceivingUser: UserModel
    private lateinit var mToolbarInfo: View
    private lateinit var mRefUser: DatabaseReference
    private lateinit var mRefMessages: DatabaseReference
    private lateinit var mAdapter: SingleChatAdapter
    private lateinit var mRecyclerView: RecyclerView
    private lateinit var mMessagesListener: AppChildEventListener
    private var mCountMessages = 10
    private var mIsScrolling = false
    private var mSmoothScrollToPosition = true
    private lateinit var mSwipeRefreshLayout: SwipeRefreshLayout
    private lateinit var mLayoutManager: LinearLayoutManager
    private lateinit var mAppVoiceRecorder: AppVoiceRecorder
    private lateinit var mBottomSheetBehavior: BottomSheetBehavior<*>

    override fun onResume() {
        super.onResume()
        initFields()
        initToolbar()
        initRecycleView()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initFields() {
        setHasOptionsMenu(true)
        mBottomSheetBehavior= BottomSheetBehavior.from(view?.findViewById(R.id.bottom_sheet_choice)!!)
        mBottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        mAppVoiceRecorder = AppVoiceRecorder()
        mSwipeRefreshLayout = view?.findViewById(R.id.chat_swipe_refresh)!!
        mLayoutManager = LinearLayoutManager(this.context)
        view?.findViewById<EditText>(R.id.chat_input_message)?.addTextChangedListener(AppTextWatcher {
            val string = view?.findViewById<EditText>(R.id.chat_input_message)?.text.toString()
            if (string.isEmpty() || string == getString(R.string.record)) {
                view?.findViewById<ImageView>(R.id.chat_btn_send_message)?.visibility = View.GONE
                view?.findViewById<ImageView>(R.id.chat_btn_attach)?.visibility = View.VISIBLE
                view?.findViewById<ImageView>(R.id.chat_btn_voice)?.visibility = View.VISIBLE
            } else {
                view?.findViewById<ImageView>(R.id.chat_btn_send_message)?.visibility = View.VISIBLE
                view?.findViewById<ImageView>(R.id.chat_btn_attach)?.visibility = View.GONE
                view?.findViewById<ImageView>(R.id.chat_btn_voice)?.visibility = View.GONE
            }
        })
        view?.findViewById<ImageView>(R.id.chat_btn_attach)?.setOnClickListener { attach() }
        CoroutineScope(Dispatchers.IO).launch {
            view?.findViewById<ImageView>(R.id.chat_btn_voice)?.setOnTouchListener { v, event ->
                if (checkPermission(RECORD_AUDIO)) {
                    if (event.action == MotionEvent.ACTION_DOWN) {
                        view?.findViewById<EditText>(R.id.chat_input_message)?.setText(getString(R.string.record))
                        view?.findViewById<ImageView>(R.id.chat_btn_voice)?.setColorFilter(
                            ContextCompat.getColor(
                                APP_ACTIVITY,
                                R.color.purple_200
                            )
                        )
                        val messageKey = getMessageKeyGroup(contact.id)
                        mAppVoiceRecorder.startRecord(messageKey)
                    } else if (event.action == MotionEvent.ACTION_UP) {
                        view?.findViewById<EditText>(R.id.chat_input_message)?.setText("")
                        view?.findViewById<ImageView>(R.id.chat_btn_voice)?.colorFilter = null
                        mAppVoiceRecorder.stopRecord { file, messageKey ->
                            uploadFileToStorage(Uri.fromFile(file),messageKey,contact.id, TYPE_MESSAGE_VOICE)
                            mSmoothScrollToPosition = true
                        }
                    }
                }
                true
            }
        }
    }

    private fun attach() {
        mBottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
        view?.findViewById<ImageView>(R.id.btn_attach_file)?.setOnClickListener { attachFile() }
        view?.findViewById<ImageView>(R.id.btn_attach_image)?.setOnClickListener { attachImage() }
        view?.findViewById<ImageView>(R.id.btn_attach_to_close)?.setOnClickListener { attachToclose() }
    }

    private fun attachToclose() {
        mBottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
    }

    private fun attachFile() {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "*/*"
        startActivityForResult(intent, PICK_FILE_REQUEST_CODE)
    }

    private fun attachImage() {
        CropImage.activity()
            .setAspectRatio(1, 1)
            .setRequestedSize(250, 250)
            .start(APP_ACTIVITY, this)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        /* Активность которая запускается для получения файла */
        super.onActivityResult(requestCode, resultCode, data)
        if (data!=null){
            when(requestCode){
                CropImage.CROP_IMAGE_ACTIVITY_REQUEST_CODE -> {
                    val uri = CropImage.getActivityResult(data).uri
                    val messageKey = getMessageKey(contact.id)
                    uploadFileToStorage(uri,messageKey,contact.id, TYPE_MESSAGE_IMAGE)
                    mSmoothScrollToPosition = true
                }


                PICK_FILE_REQUEST_CODE -> {
                    val uri = data.data
                    val messageKey = getMessageKeyPrivate(contact.id)
                    val filename = getFilenameFromUri(uri!!)
                    uploadFileToStorage(uri,messageKey,contact.id, TYPE_MESSAGE_FILE,filename)
                    uri?.let { uploadFileToStorage(it,messageKey,contact.id, TYPE_MESSAGE_FILE) }
                    mSmoothScrollToPosition = true
                }
            }
        }
    }

    private fun initRecycleView() {
        mRecyclerView = view?.findViewById(R.id.chat_recycle_view) !!
        mAdapter = SingleChatAdapter()
        mRefMessages = REF_DATABASE_ROOT
            .child(NODE_MESSAGES)
            .child(CURRENT_UID)
            .child(contact.id)
        mRecyclerView.adapter = mAdapter
        mRecyclerView.setHasFixedSize(true)
        mRecyclerView.isNestedScrollingEnabled = false
        mRecyclerView.layoutManager = mLayoutManager
        mMessagesListener = AppChildEventListener {
            val message = it.getCommonModel()

            if (mSmoothScrollToPosition) {
                mAdapter.addItemToBottom(AppViewFactory.getView(message)) {
                    mRecyclerView.smoothScrollToPosition(mAdapter.itemCount)
                }
            } else {
                mAdapter.addItemToTop(AppViewFactory.getView(message)) {
                    mSwipeRefreshLayout.isRefreshing = false
                }
            }

        }
        mRefMessages.limitToLast(mCountMessages).addChildEventListener(mMessagesListener)

        mRecyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {

            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                println(mRecyclerView.recycledViewPool.getRecycledViewCount(0))
                if (mIsScrolling && dy < 0 && mLayoutManager.findFirstVisibleItemPosition() <= 3) {
                    updateData()
                }
            }

            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                if (newState == AbsListView.OnScrollListener.SCROLL_STATE_TOUCH_SCROLL) {
                    mIsScrolling = true
                }
            }
        })


        mSwipeRefreshLayout.setOnRefreshListener { updateData() }
    }

    private fun updateData() {
        mSmoothScrollToPosition = false
        mIsScrolling = false
        mCountMessages += 10
        mRefMessages.removeEventListener(mMessagesListener)
        mRefMessages.limitToLast(mCountMessages).addChildEventListener(mMessagesListener)
    }

    private fun initToolbar() {
        mToolbarInfo = APP_ACTIVITY.mToolbar.findViewById<View>(R.id.toolbar_info)
        mToolbarInfo.visibility = View.VISIBLE
        mListenerInfoToolbar = AppValueEventListener {
            mReceivingUser = it.getUserModel()
            initInfoToolbar()
        }

        mRefUser = REF_DATABASE_ROOT.child(NODE_USERS).child(contact.id)
        mRefUser.addValueEventListener(mListenerInfoToolbar)

        view?.findViewById<ImageView>(R.id.chat_btn_send_message)?.setOnClickListener {
            mSmoothScrollToPosition = true
            val message = view?.findViewById<EditText>(R.id.chat_input_message)?.text.toString()
            if (message.isEmpty()) {
                showToast(getString(R.string.enter_a_message))
            } else sendMessage(message, contact.id, TYPE_TEXT) {
                saveToMainList(contact.id, TYPE_CHAT)
                view?.findViewById<EditText>(R.id.chat_input_message)?.setText("")
            }
        }
    }

    private fun initInfoToolbar() {
        if (mReceivingUser.fullname.isEmpty()) {
            mToolbarInfo.findViewById<TextView>(R.id.toolbar_chat_fullname).text = contact.fullname
        } else mToolbarInfo.findViewById<TextView>(R.id.toolbar_chat_fullname).text =
            mReceivingUser.fullname
        mToolbarInfo.findViewById<CircleImageView>(R.id.toolbar_chat_image)
            .downloadAndSetImage(mReceivingUser.photoUrl)
        mToolbarInfo.findViewById<TextView>(R.id.toolbar_chat_status).text = mReceivingUser.state
    }

    override fun onPause() {
        super.onPause()
        mToolbarInfo.visibility = View.GONE
        mRefUser.removeEventListener(mListenerInfoToolbar)
        mRefMessages.removeEventListener(mMessagesListener)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mAppVoiceRecorder.releaseRecorder()
        mAdapter.onDestroy()
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        /* Создания выпадающего меню*/
        activity?.menuInflater?.inflate(R.menu.single_chat_action_menu, menu)
    }
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        /* Слушатель выбора пунктов выпадающего меню */
        when (item.itemId) {
            R.id.menu_clear_chat -> clearChat(contact.id){
                showToast(getString(R.string.chat_cleared))
                replaceFragment(MainListFragment())
            }
            R.id.menu_remove_chat -> removeChat(contact.id){
                showToast(getString(R.string.chat_remove))
                replaceFragment(MainListFragment())
            }
            R.id.menu_delete_chat -> deleteChat(contact.id){
                showToast(getString(R.string.chat_deleted))
                replaceFragment(MainListFragment())
            }
        }
        return true
    }
}