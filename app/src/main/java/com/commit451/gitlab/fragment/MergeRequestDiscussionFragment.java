package com.commit451.gitlab.fragment;

import android.app.ActivityOptions;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.commit451.easycallback.EasyCallback;
import com.commit451.gitlab.App;
import com.commit451.gitlab.R;
import com.commit451.gitlab.activity.AttachActivity;
import com.commit451.gitlab.adapter.MergeRequestDetailAdapter;
import com.commit451.gitlab.event.MergeRequestChangedEvent;
import com.commit451.gitlab.model.api.FileUploadResponse;
import com.commit451.gitlab.model.api.MergeRequest;
import com.commit451.gitlab.model.api.Note;
import com.commit451.gitlab.model.api.Project;
import com.commit451.gitlab.navigation.TransitionFactory;
import com.commit451.gitlab.util.LinkHeaderParser;
import com.commit451.gitlab.view.SendMessageView;
import com.commit451.teleprinter.Teleprinter;

import org.greenrobot.eventbus.Subscribe;
import org.parceler.Parcels;

import java.util.List;

import butterknife.BindView;
import timber.log.Timber;

import static android.app.Activity.RESULT_OK;

/**
 * Shows the discussion of a merge request
 */
public class MergeRequestDiscussionFragment extends ButterKnifeFragment {

    private static final String KEY_PROJECT = "project";
    private static final String KEY_MERGE_REQUEST = "merge_request";

    private static final int REQUEST_ATTACH = 1;

    public static MergeRequestDiscussionFragment newInstance(Project project, MergeRequest mergeRequest) {
        MergeRequestDiscussionFragment fragment = new MergeRequestDiscussionFragment();
        Bundle args = new Bundle();
        args.putParcelable(KEY_PROJECT, Parcels.wrap(project));
        args.putParcelable(KEY_MERGE_REQUEST, Parcels.wrap(mergeRequest));
        fragment.setArguments(args);
        return fragment;
    }

    @BindView(R.id.root)
    ViewGroup mRoot;
    @BindView(R.id.swipe_layout)
    SwipeRefreshLayout mSwipeRefreshLayout;
    @BindView(R.id.list)
    RecyclerView mNotesRecyclerView;
    @BindView(R.id.send_message_view)
    SendMessageView mSendMessageView;
    @BindView(R.id.progress)
    View mProgress;

    MergeRequestDetailAdapter mMergeRequestDetailAdapter;
    LinearLayoutManager mNotesLinearLayoutManager;

    Project mProject;
    MergeRequest mMergeRequest;
    Uri mNextPageUrl;
    boolean mLoading;
    Teleprinter mTeleprinter;

    EventReceiver mEventReceiver;

    private final RecyclerView.OnScrollListener mOnScrollListener = new RecyclerView.OnScrollListener() {
        @Override
        public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
            super.onScrolled(recyclerView, dx, dy);
            int visibleItemCount = mNotesLinearLayoutManager.getChildCount();
            int totalItemCount = mNotesLinearLayoutManager.getItemCount();
            int firstVisibleItem = mNotesLinearLayoutManager.findFirstVisibleItemPosition();
            if (firstVisibleItem + visibleItemCount >= totalItemCount && !mLoading && mNextPageUrl != null) {
                loadMoreNotes();
            }
        }
    };

    private EasyCallback<List<Note>> mNotesCallback = new EasyCallback<List<Note>>() {

        @Override
        public void success(@NonNull List<Note> response) {
            if (getView() == null) {
                return;
            }
            mSwipeRefreshLayout.setRefreshing(false);
            mLoading = false;
            mNextPageUrl = LinkHeaderParser.parse(getResponse()).getNext();
            mMergeRequestDetailAdapter.setNotes(response);
        }

        @Override
        public void failure(Throwable t) {
            mLoading = false;
            Timber.e(t, null);
            if (getView() == null) {
                return;
            }
            mSwipeRefreshLayout.setRefreshing(false);
            Snackbar.make(mRoot, getString(R.string.connection_error), Snackbar.LENGTH_SHORT)
                    .show();
        }
    };

    private EasyCallback<List<Note>> mMoreNotesCallback = new EasyCallback<List<Note>>() {

        @Override
        public void success(@NonNull List<Note> response) {
            if (getView() == null) {
                return;
            }
            mMergeRequestDetailAdapter.setLoading(false);
            mLoading = false;
            mNextPageUrl = LinkHeaderParser.parse(getResponse()).getNext();
            mMergeRequestDetailAdapter.addNotes(response);
        }

        @Override
        public void failure(Throwable t) {
            if (getView() == null) {
                return;
            }
            mLoading = false;
            Timber.e(t, null);
            mMergeRequestDetailAdapter.setLoading(false);
            Snackbar.make(mRoot, getString(R.string.connection_error), Snackbar.LENGTH_SHORT)
                    .show();
        }
    };

    private EasyCallback<Note> mPostNoteCallback = new EasyCallback<Note>() {

        @Override
        public void success(@NonNull Note response) {
            if (getView() == null) {
                return;
            }
            mProgress.setVisibility(View.GONE);
            mMergeRequestDetailAdapter.addNote(response);
            mNotesRecyclerView.smoothScrollToPosition(MergeRequestDetailAdapter.getHeaderCount());
        }

        @Override
        public void failure(Throwable t) {
            if (getView() == null) {
                return;
            }
            Timber.e(t, null);
            mProgress.setVisibility(View.GONE);
            Snackbar.make(mRoot, getString(R.string.connection_error), Snackbar.LENGTH_SHORT)
                    .show();
        }
    };

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mProject = Parcels.unwrap(getArguments().getParcelable(KEY_PROJECT));
        mMergeRequest = Parcels.unwrap(getArguments().getParcelable(KEY_MERGE_REQUEST));
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_merge_request_discussion, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mTeleprinter = new Teleprinter(getActivity());

        mMergeRequestDetailAdapter = new MergeRequestDetailAdapter(getActivity(), mMergeRequest, mProject);
        mNotesLinearLayoutManager = new LinearLayoutManager(getActivity());
        mNotesRecyclerView.setLayoutManager(mNotesLinearLayoutManager);
        mNotesRecyclerView.setAdapter(mMergeRequestDetailAdapter);
        mNotesRecyclerView.addOnScrollListener(mOnScrollListener);

        mSendMessageView.setCallbacks(new SendMessageView.Callbacks() {
            @Override
            public void onSendClicked(String message) {
                postNote(message);
            }

            @Override
            public void onAttachmentClicked() {
                Intent intent = AttachActivity.newIntent(getActivity(), mProject);
                ActivityOptions activityOptions = TransitionFactory.createFadeInOptions(getActivity());
                startActivityForResult(intent, REQUEST_ATTACH, activityOptions.toBundle());
            }
        });

        mSwipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                loadNotes();
            }
        });
        loadNotes();

        mEventReceiver = new EventReceiver();
        App.bus().register(mEventReceiver);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case REQUEST_ATTACH:
                if (resultCode == RESULT_OK) {
                    FileUploadResponse response = Parcels.unwrap(data.getParcelableExtra(AttachActivity.KEY_FILE_UPLOAD_RESPONSE));
                    mProgress.setVisibility(View.GONE);
                    mSendMessageView.appendText(response.getMarkdown());
                } else {
                    Snackbar.make(mRoot, R.string.failed_to_upload_file, Snackbar.LENGTH_LONG)
                            .show();
                }
                break;
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        App.bus().unregister(mEventReceiver);
    }

    private void loadNotes() {
        mSwipeRefreshLayout.post(new Runnable() {
            @Override
            public void run() {
                if (mSwipeRefreshLayout != null) {
                    mSwipeRefreshLayout.setRefreshing(true);
                }
            }
        });
        App.instance().getGitLab().getMergeRequestNotes(mProject.getId(), mMergeRequest.getId()).enqueue(mNotesCallback);
    }

    private void loadMoreNotes() {
        mMergeRequestDetailAdapter.setLoading(true);
        App.instance().getGitLab().getMergeRequestNotes(mNextPageUrl.toString()).enqueue(mMoreNotesCallback);
    }

    private void postNote(String message) {

        if (message.length() < 1) {
            return;
        }

        mProgress.setVisibility(View.VISIBLE);
        mProgress.setAlpha(0.0f);
        mProgress.animate().alpha(1.0f);
        // Clear text & collapse keyboard
        mTeleprinter.hideKeyboard();
        mSendMessageView.clearText();

        App.instance().getGitLab().addMergeRequestNote(mProject.getId(), mMergeRequest.getId(), message).enqueue(mPostNoteCallback);
    }

    private class EventReceiver {

        @Subscribe
        public void onMergeRequestChangedEvent(MergeRequestChangedEvent event) {
            if (mMergeRequest.getId() == event.mergeRequest.getId()) {
                mMergeRequest = event.mergeRequest;
                loadNotes();
            }
        }
    }

}
