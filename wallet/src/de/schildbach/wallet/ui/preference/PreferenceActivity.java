/*
 * Copyright 2011-2015 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package de.schildbach.wallet.ui.preference;

import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.graphics.Insets;
import androidx.core.view.OnApplyWindowInsetsListener;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import subgeneius.dobbs.wallet.R;

public final class PreferenceActivity extends AppCompatActivity
        implements PreferenceFragmentCompat.OnPreferenceStartFragmentCallback
{
    @Override
    protected void onCreate(final Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.preference_activity);

        final Toolbar toolbar = findViewById(R.id.preference_toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null)
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        // SDK 35+ enforces edge-to-edge: system bars overlay the window. Push the
        // toolbar/content down by the status-bar inset and clear the nav-bar inset
        // at the bottom so the entire screen is reachable.
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.preference_root),
                new OnApplyWindowInsetsListener() {
                    @Override
                    public WindowInsetsCompat onApplyWindowInsets(final View v,
                            final WindowInsetsCompat insets) {
                        final Insets bars = insets.getInsets(
                                WindowInsetsCompat.Type.systemBars()
                                        | WindowInsetsCompat.Type.displayCutout());
                        v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                        return WindowInsetsCompat.CONSUMED;
                    }
                });

        if (savedInstanceState == null)
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.preference_content, new HeadersFragment())
                    .commit();
    }

    @Override
    public boolean onPreferenceStartFragment(final PreferenceFragmentCompat caller, final Preference pref)
    {
        final Fragment fragment = getSupportFragmentManager().getFragmentFactory().instantiate(
                getClassLoader(), pref.getFragment());
        fragment.setArguments(pref.getExtras());
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.preference_content, fragment)
                .addToBackStack(null)
                .commit();
        if (getSupportActionBar() != null && pref.getTitle() != null)
            getSupportActionBar().setTitle(pref.getTitle());
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item)
    {
        if (item.getItemId() == android.R.id.home) {
            if (getSupportFragmentManager().getBackStackEntryCount() > 0) {
                getSupportFragmentManager().popBackStack();
                if (getSupportActionBar() != null)
                    getSupportActionBar().setTitle(R.string.preferences_activity_title);
            } else {
                finish();
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
