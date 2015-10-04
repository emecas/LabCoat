package com.commit451.gitlab.activities;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.support.design.widget.TabLayout;
import android.support.v4.view.ViewPager;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;

import com.commit451.gitlab.GitLabApp;
import com.commit451.gitlab.R;
import com.commit451.gitlab.adapter.SectionsPagerAdapter;
import com.commit451.gitlab.api.GitLabClient;
import com.commit451.gitlab.events.ProjectReloadEvent;
import com.commit451.gitlab.fragments.CommitsFragment;
import com.commit451.gitlab.fragments.FilesFragment;
import com.commit451.gitlab.fragments.IssuesFragment;
import com.commit451.gitlab.fragments.MergeRequestFragment;
import com.commit451.gitlab.fragments.MembersFragment;
import com.commit451.gitlab.fragments.OverviewFragment;
import com.commit451.gitlab.model.Branch;
import com.commit451.gitlab.model.Project;
import com.commit451.gitlab.tools.IntentUtil;

import org.parceler.Parcels;

import java.util.List;

import butterknife.Bind;
import butterknife.ButterKnife;
import retrofit.Callback;
import retrofit.Response;
import retrofit.Retrofit;
import timber.log.Timber;

public class ProjectActivity extends BaseActivity {

    private static final String EXTRA_PROJECT = "extra_project";

	public static Intent newInstance(Context context, Project project) {
		Intent intent = new Intent(context, ProjectActivity.class);
        intent.putExtra(EXTRA_PROJECT, Parcels.wrap(project));
		return intent;
	}

	@Bind(R.id.toolbar) Toolbar toolbar;
	@Bind(R.id.tabs) TabLayout tabs;
	@Bind(R.id.branch_spinner) Spinner branchSpinner;
    @Bind(R.id.progress) View progress;
	@Bind(R.id.pager) ViewPager viewPager;

	private final AdapterView.OnItemSelectedListener spinnerItemSelectedListener = new AdapterView.OnItemSelectedListener() {
		@Override
		public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
			mBranchName = ((TextView)view).getText().toString();
			broadcastLoad();
		}

		@Override
		public void onNothingSelected(AdapterView<?> parent) { }
	};

    Project mProject;
    String mBranchName;

	private Callback<List<Branch>> mBranchesCallback = new Callback<List<Branch>>() {

		@Override
		public void onResponse(Response<List<Branch>> response, Retrofit retrofit) {
			if (!response.isSuccess()) {
				return;
			}
			progress.setVisibility(View.GONE);


			// Set up the dropdown list navigation in the action bar.
			branchSpinner.setAdapter(new ArrayAdapter<>(ProjectActivity.this, android.R.layout.simple_list_item_1, android.R.id.text1, response.body()));
			for (int i=0; i<response.body().size(); i++) {
				if (response.body().get(i).getName().equals(mProject.getDefaultBranch())) {
					branchSpinner.setSelection(i);
				}
			}

			branchSpinner.setOnItemSelectedListener(spinnerItemSelectedListener);

			if(response.body().isEmpty()) {
				broadcastLoad();
			}
		}

		@Override
		public void onFailure(Throwable t) {
			progress.setVisibility(View.GONE);
			Timber.e(t.toString());
			Snackbar.make(getWindow().getDecorView(), getString(R.string.connection_error), Snackbar.LENGTH_SHORT)
					.show();
		}
	};

	private final Toolbar.OnMenuItemClickListener mOnMenuItemClickListener = new Toolbar.OnMenuItemClickListener() {
		@Override
		public boolean onMenuItemClick(MenuItem item) {
			switch (item.getItemId()) {
				case R.id.action_share:
					IntentUtil.share(getWindow().getDecorView(), mProject.getWebUrl());
					return true;
			}
			return false;
		}
	};
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		ButterKnife.bind(this);
        mProject = Parcels.unwrap(getIntent().getParcelableExtra(EXTRA_PROJECT));

		toolbar.setNavigationIcon(R.drawable.ic_back_24dp);
		toolbar.setNavigationOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				onBackPressed();
			}
		});
		toolbar.inflateMenu(R.menu.menu_repository);
		toolbar.setOnMenuItemClickListener(mOnMenuItemClickListener);

		SectionsPagerAdapter sectionsPagerAdapter = new SectionsPagerAdapter(this, getSupportFragmentManager());

		viewPager.setAdapter(sectionsPagerAdapter);
		tabs.setupWithViewPager(viewPager);
        loadBranches();
	}

    private void loadBranches() {
        GitLabClient.instance().getBranches(mProject.getId()).enqueue(mBranchesCallback);
    }

    private void broadcastLoad() {
        GitLabApp.bus().post(new ProjectReloadEvent(mProject, mBranchName));
    }
	
	@Override
	public void onBackPressed() {
		boolean handled = false;
		
		switch(viewPager.getCurrentItem()) {
			case 0:
				OverviewFragment overviewFragment = (OverviewFragment) getSupportFragmentManager().findFragmentByTag("android:switcher:" + R.id.pager + ":0");
				handled = overviewFragment.onBackPressed();
				break;
			case 1:
				CommitsFragment commitsFragment = (CommitsFragment) getSupportFragmentManager().findFragmentByTag("android:switcher:" + R.id.pager + ":1");
				handled = commitsFragment.onBackPressed();
				break;
			case 2:
				IssuesFragment issuesFragment = (IssuesFragment) getSupportFragmentManager().findFragmentByTag("android:switcher:" + R.id.pager + ":2");
				handled = issuesFragment.onBackPressed();
				break;
			case 3:
				FilesFragment filesFragment = (FilesFragment) getSupportFragmentManager().findFragmentByTag("android:switcher:" + R.id.pager + ":3");
				handled = filesFragment.onBackPressed();
				break;
			case 4:
				MergeRequestFragment mergeRequestFragment = (MergeRequestFragment) getSupportFragmentManager().findFragmentByTag("android:switcher:" + R.id.pager + ":4");
				handled = mergeRequestFragment.onBackPressed();
				break;
            case 5:
                MembersFragment membersFragment = (MembersFragment) getSupportFragmentManager().findFragmentByTag("android:switcher:" + R.id.pager + ":5");
                handled = membersFragment.onBackPressed();
                break;
		}
		
		if(!handled) {
            super.onBackPressed();
        }
	}

	public String getBranchName() {
		return mBranchName;
	}

    public Project getProject() {
        return mProject;
    }
}