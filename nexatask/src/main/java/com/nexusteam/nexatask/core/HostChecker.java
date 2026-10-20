package com.nexusteam.nexatask.core;

import android.app.Activity;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

/** Determines whether a host Activity/Fragment is still safe for UI updates. */
public final class HostChecker {
    private HostChecker() {}

    public static boolean isAlive(@Nullable Object tag) {
        if (tag == null) return true;
        if (tag instanceof Activity) return isActivityAlive((Activity) tag);
        if (tag instanceof Fragment) return isFragmentAlive((Fragment) tag);
        return true;
    }

    public static boolean isActivityAlive(@Nullable Activity a) {
        if (a == null) return false;
        if (a.isFinishing()) return false;
        try { return !a.isDestroyed(); } catch (Throwable t) { return true; }
    }

    public static boolean isFragmentAlive(@Nullable Fragment f) {
        if (f == null) return false;
        if (!f.isAdded() || f.isDetached() || f.isRemoving()) return false;
        return isActivityAlive(f.getActivity());
    }
}
