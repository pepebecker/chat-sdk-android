/*
 * Created by Itzik Braun on 12/3/2015.
 * Copyright (c) 2015 deluge. All rights reserved.
 *
 * Last Modification at: 3/12/15 4:27 PM
 */

package co.chatsdk.ui.threads;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import androidx.annotation.LayoutRes;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import android.view.MenuItem;
import android.view.View;

import com.facebook.drawee.view.SimpleDraweeView;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;

import co.chatsdk.core.dao.Thread;
import co.chatsdk.core.dao.ThreadMetaValue;
import co.chatsdk.core.events.NetworkEvent;
import co.chatsdk.core.session.ChatSDK;
import co.chatsdk.core.session.InterfaceManager;
import co.chatsdk.core.session.StorageManager;
import co.chatsdk.core.utils.DisposableList;
import co.chatsdk.core.utils.Strings;
import co.chatsdk.ui.R;
import co.chatsdk.ui.chat.ChatActivity;
import co.chatsdk.ui.contacts.ContactsFragment;
import co.chatsdk.ui.helpers.ProfilePictureChooserOnClickListener;
import co.chatsdk.ui.main.BaseActivity;
import io.reactivex.disposables.Disposable;

/**
 * Created by braunster on 24/11/14.
 */
public class ThreadDetailsActivity extends BaseActivity {

    /** Set true if you want slide down animation for this context exit. */
    protected boolean animateExit = false;

    protected Thread thread;
    protected SimpleDraweeView threadImageView;

    protected ContactsFragment contactsFragment;
    protected DisposableList disposableList = new DisposableList();

    protected ActionBar actionBar;

    private Gson gson = new Gson();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState != null) {
            getDataFromBundle(savedInstanceState);
        }
        else {
            if (getIntent().getExtras() != null) {
                getDataFromBundle(getIntent().getExtras());
            }
            else {
                finish();
            }
        }

        setContentView(activityLayout());

        initViews();

        disposableList.add(ChatSDK.events().sourceOnMain()
                .filter(NetworkEvent.threadUsersUpdated())
                .subscribe(networkEvent -> loadData()));

        loadData();
    }

    protected @LayoutRes int activityLayout() {
        return R.layout.chat_sdk_activity_thread_details;
    }

    protected void initViews() {
        actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setTitle(Strings.nameForThread(thread));
            actionBar.setHomeButtonEnabled(true);
        }

        final View actionBarView = getLayoutInflater().inflate(R.layout.chat_sdk_activity_thread_details, null);

        // Allow the thread name to be modified by a long click
        actionBarView.setOnLongClickListener(v -> {
            // TODO: Implement this
            return true;
        });

        threadImageView = findViewById(R.id.chat_sdk_thread_image_view);
    }

    protected void loadData () {

        ThreadImageBuilder.load(threadImageView, thread);

        // CoreThread users bundle
        contactsFragment = new ContactsFragment();
        contactsFragment.setInflateMenu(false);
        contactsFragment.setLoadingMode(ContactsFragment.MODE_LOAD_THREAD_USERS);
        contactsFragment.setExtraData(thread.getEntityID());
        contactsFragment.setClickMode(ContactsFragment.CLICK_MODE_SHOW_PROFILE);

        String adminsJson = "";
        ThreadMetaValue adminsMetaValue = thread.metaValueForKey("admins");
        if (adminsMetaValue != null) adminsJson = adminsMetaValue.getValue();
        ArrayList<String> adminsFromJson = gson.fromJson(adminsJson, new TypeToken<List<String>>(){}.getType());
        final ArrayList<String> admins = (adminsFromJson != null ? adminsFromJson : new ArrayList<>());

        Disposable d = contactsFragment.onLongClickObservable().subscribe(user -> {
            if (thread.getUsers().size() > 2 && !user.isMe()) {
                String option1 = "Make user group admin";
                boolean isAdmin = admins.contains(user.getEntityID());
                if (isAdmin) option1 = "Remove admin rights from user";
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                String[] options = {option1, "Remove user from group"};
                builder.setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        if (isAdmin) {
                            admins.remove(user.getEntityID());
                        } else {
                            admins.add(user.getEntityID());
                        }
                        thread.setMetaValue("admins", gson.toJson(admins));
                        ChatSDK.thread().pushThreadMeta(thread).subscribe();
                    }
                    if (which == 1) {
                        disposableList.add(ChatSDK.thread().removeUsersFromThread(thread, user).subscribe(() -> {
                            contactsFragment.reloadData();
                        }));
                    }
                });
                builder.show();
            }
        });

        getSupportFragmentManager().beginTransaction().replace(R.id.frame_thread_users, contactsFragment).commit();
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Only if the current user is the admin of this thread.
        if (StringUtils.isNotBlank(thread.getCreatorEntityId()) && thread.getCreatorEntityId().equals(ChatSDK.currentUserID())) {
            //threadImageView.setOnClickListener(ChatSDKIntentClickListener.getPickImageClickListener(this, THREAD_PIC));
            threadImageView.setOnClickListener(new ProfilePictureChooserOnClickListener(this));
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    public void onBackPressed() {
        setResult(AppCompatActivity.RESULT_OK);

        finish();
        if (animateExit) {
            overridePendingTransition(R.anim.dummy, R.anim.slide_top_bottom_out);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // TODO: Enable thread images
    }

    @Override
    protected void onStop() {
        disposableList.dispose();
        super.onStop();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        getDataFromBundle(intent.getExtras());
    }

    protected void getDataFromBundle(Bundle bundle){
        if (bundle == null) {
            return;
        }

        animateExit = bundle.getBoolean(ChatActivity.ANIMATE_EXIT, animateExit);

        String threadEntityID = bundle.getString(InterfaceManager.THREAD_ENTITY_ID);

        if(threadEntityID != null && threadEntityID.length() > 0) {
            thread = StorageManager.shared().fetchThreadWithEntityID(threadEntityID);
        }
        else {
            finish();
        }

    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(InterfaceManager.THREAD_ENTITY_ID, thread.getEntityID());
        outState.putBoolean(ChatActivity.ANIMATE_EXIT, animateExit);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        if (item.getItemId() == android.R.id.home)
        {
            onBackPressed();
        }
        return true;
    }


}
