package org.robolectric.shadows;

import static android.os.Build.VERSION_CODES.JELLY_BEAN;
import static android.os.Build.VERSION_CODES.LOLLIPOP;
import static android.os.Build.VERSION_CODES.M;
import static android.os.Build.VERSION_CODES.N;
import static org.robolectric.shadow.api.Shadow.directlyOn;
import static org.robolectric.util.reflector.Reflector.reflector;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityThread;
import android.app.Application;
import android.app.Dialog;
import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.database.Cursor;
import android.os.Bundle;
import android.text.Selection;
import android.text.SpannableStringBuilder;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.HiddenApi;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.LooperMode;
import org.robolectric.annotation.RealObject;
import org.robolectric.fakes.RoboMenuItem;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.util.ReflectionHelpers;
import org.robolectric.util.ReflectionHelpers.ClassParameter;
import org.robolectric.util.reflector.WithType;

@SuppressWarnings("NewApi")
@Implements(Activity.class)
public class ShadowActivity extends ShadowContextThemeWrapper {

  @RealObject
  protected Activity realActivity;

  private int resultCode;
  private Intent resultIntent;
  private Activity parent;
  private int requestedOrientation = -1;
  private View currentFocus;
  private Integer lastShownDialogId = null;
  private int pendingTransitionEnterAnimResId = -1;
  private int pendingTransitionExitAnimResId = -1;
  private Object lastNonConfigurationInstance;
  private Map<Integer, Dialog> dialogForId = new HashMap<>();
  private ArrayList<Cursor> managedCursors = new ArrayList<>();
  private int mDefaultKeyMode = Activity.DEFAULT_KEYS_DISABLE;
  private SpannableStringBuilder mDefaultKeySsb = null;
  private int streamType = -1;
  private boolean mIsTaskRoot = true;
  private Menu optionsMenu;
  private ComponentName callingActivity;
  private String callingPackage;
  private PermissionsRequest lastRequestedPermission;
  private ActivityController controller;
  private boolean inMultiWindowMode = false;
  private IntentSenderRequest lastIntentSenderRequest;
  private boolean throwIntentSenderException;

  public void setApplication(Application application) {
    reflector(_Activity_.class, realActivity).setApplication(application);
  }

  public void callAttach(Intent intent) {
    callAttach(intent, /*lastNonConfigurationInstances=*/ null);
  }

  public void callAttach(
      Intent intent,
      @Nullable @WithType("android.app.Activity$NonConfigurationInstances")
          Object lastNonConfigurationInstances) {
    Application application = RuntimeEnvironment.application;
    Context baseContext = application.getBaseContext();

    ComponentName componentName =
        new ComponentName(application.getPackageName(), realActivity.getClass().getName());
    ActivityInfo activityInfo;
    PackageManager packageManager = application.getPackageManager();
    shadowOf(packageManager).addActivityIfNotPresent(componentName);
    try {
      activityInfo = packageManager.getActivityInfo(componentName, PackageManager.GET_META_DATA);
    } catch (NameNotFoundException e) {
      throw new RuntimeException("Activity is not resolved even if we made sure it exists", e);
    }

    CharSequence activityTitle = activityInfo.loadLabel(baseContext.getPackageManager());

    ActivityThread activityThread = (ActivityThread) RuntimeEnvironment.getActivityThread();
    Instrumentation instrumentation = activityThread.getInstrumentation();

    reflector(_Activity_.class, realActivity)
        .callAttach(
            baseContext,
            activityThread,
            instrumentation,
            application,
            intent,
            activityInfo,
            activityTitle,
            lastNonConfigurationInstances);

    int theme = activityInfo.getThemeResource();
    if (theme != 0) {
      realActivity.setTheme(theme);
    }
  }

  public void setCallingActivity(ComponentName activityName) {
    callingActivity = activityName;
  }

  @Implementation
  protected ComponentName getCallingActivity() {
    return callingActivity;
  }

  public void setCallingPackage(String packageName) {
    callingPackage = packageName;
  }

  @Implementation
  protected String getCallingPackage() {
    return callingPackage;
  }

  @Implementation
  protected void setDefaultKeyMode(int keyMode) {
    mDefaultKeyMode = keyMode;

    // Some modes use a SpannableStringBuilder to track & dispatch input events
    // This list must remain in sync with the switch in onKeyDown()
    switch (mDefaultKeyMode) {
      case Activity.DEFAULT_KEYS_DISABLE:
      case Activity.DEFAULT_KEYS_SHORTCUT:
        mDefaultKeySsb = null;      // not used in these modes
        break;
      case Activity.DEFAULT_KEYS_DIALER:
      case Activity.DEFAULT_KEYS_SEARCH_LOCAL:
      case Activity.DEFAULT_KEYS_SEARCH_GLOBAL:
        mDefaultKeySsb = new SpannableStringBuilder();
        Selection.setSelection(mDefaultKeySsb, 0);
        break;
      default:
        throw new IllegalArgumentException();
    }
  }

  public int getDefaultKeymode() {
    return mDefaultKeyMode;
  }

  @Implementation
  protected final void setResult(int resultCode) {
    this.resultCode = resultCode;
  }

  @Implementation
  protected final void setResult(int resultCode, Intent data) {
    this.resultCode = resultCode;
    this.resultIntent = data;
  }

  @Implementation
  protected LayoutInflater getLayoutInflater() {
    return LayoutInflater.from(realActivity);
  }

  @Implementation
  protected MenuInflater getMenuInflater() {
    return new MenuInflater(realActivity);
  }

  /**
   * Checks to ensure that the{@code contentView} has been set
   *
   * @param id ID of the view to find
   * @return the view
   * @throws RuntimeException if the {@code contentView} has not been called first
   */
  @Implementation
  protected View findViewById(int id) {
    return getWindow().findViewById(id);
  }

  @Implementation
  protected final Activity getParent() {
    return parent;
  }

  /**
   * Allow setting of Parent fragmentActivity (for unit testing purposes only)
   *
   * @param parent Parent fragmentActivity to set on this fragmentActivity
   */
  @HiddenApi @Implementation
  public void setParent(Activity parent) {
    this.parent = parent;
  }

  @Implementation
  protected void onBackPressed() {
    finish();
  }

  @Implementation
  protected void finish() {
    // Sets the mFinished field in the real activity so NoDisplay activities can be tested.
    ReflectionHelpers.setField(Activity.class, realActivity, "mFinished", true);
  }

  @Implementation(minSdk = LOLLIPOP)
  protected void finishAndRemoveTask() {
    // Sets the mFinished field in the real activity so NoDisplay activities can be tested.
    ReflectionHelpers.setField(Activity.class, realActivity, "mFinished", true);
  }

  @Implementation(minSdk = JELLY_BEAN)
  protected void finishAffinity() {
    // Sets the mFinished field in the real activity so NoDisplay activities can be tested.
    ReflectionHelpers.setField(Activity.class, realActivity, "mFinished", true);
  }

  public void resetIsFinishing() {
    ReflectionHelpers.setField(Activity.class, realActivity, "mFinished", false);
  }

  /**
   * Returns whether {@link #finish()} was called.
   *
   * @deprecated Use {@link Activity#isFinishing()} instead.
   */
  @Deprecated
  public boolean isFinishing() {
    return directlyOn(realActivity, Activity.class).isFinishing();
  }

  /**
   * Constructs a new Window (a {@link com.android.internal.policy.impl.PhoneWindow}) if no window
   * has previously been set.
   *
   * @return the window associated with this Activity
   */
  @Implementation
  protected Window getWindow() {
    Window window = directlyOn(realActivity, Activity.class).getWindow();

    if (window == null) {
      try {
        window = ShadowWindow.create(realActivity);
        setWindow(window);
      } catch (Exception e) {
        throw new RuntimeException("Window creation failed!", e);
      }
    }

    return window;
  }

  public void setWindow(Window window) {
    reflector(_Activity_.class, realActivity).setWindow(window);
  }

  @Implementation
  protected void runOnUiThread(Runnable action) {
    if (ShadowLooper.looperMode() == LooperMode.Mode.PAUSED) {
      directlyOn(
          realActivity,
          Activity.class,
          "runOnUiThread",
          ClassParameter.from(Runnable.class, action));
    } else {
      ShadowApplication.getInstance().getForegroundThreadScheduler().post(action);
    }
  }

  @Implementation
  protected void setRequestedOrientation(int requestedOrientation) {
    if (getParent() != null) {
      getParent().setRequestedOrientation(requestedOrientation);
    } else {
      this.requestedOrientation = requestedOrientation;
    }
  }

  @Implementation
  protected int getRequestedOrientation() {
    if (getParent() != null) {
      return getParent().getRequestedOrientation();
    } else {
      return this.requestedOrientation;
    }
  }

  @Implementation
  protected int getTaskId() {
    return 0;
  }

  @Implementation
  public void startIntentSenderForResult(
      IntentSender intentSender,
      int requestCode,
      @Nullable Intent fillInIntent,
      int flagsMask,
      int flagsValues,
      int extraFlags,
      Bundle options)
      throws IntentSender.SendIntentException {
    if (throwIntentSenderException) {
      throw new IntentSender.SendIntentException("PendingIntent was canceled");
    }
    lastIntentSenderRequest =
        new IntentSenderRequest(
            intentSender, requestCode, fillInIntent, flagsMask, flagsValues, extraFlags, options);
  }

  /**
   * @return the {@code contentView} set by one of the {@code setContentView()} methods
   */
  public View getContentView() {
    return ((ViewGroup) getWindow().findViewById(android.R.id.content)).getChildAt(0);
  }

  /**
   * @return the {@code resultCode} set by one of the {@code setResult()} methods
   */
  public int getResultCode() {
    return resultCode;
  }

  /**
   * @return the {@code Intent} set by {@link #setResult(int, android.content.Intent)}
   */
  public Intent getResultIntent() {
    return resultIntent;
  }

  /**
   * Consumes and returns the next {@code Intent} on the
   * started activities for results stack.
   *
   * @return the next started {@code Intent} for an activity, wrapped in
   *         an {@link ShadowActivity.IntentForResult} object
   */
  public IntentForResult getNextStartedActivityForResult() {
    ActivityThread activityThread = (ActivityThread) RuntimeEnvironment.getActivityThread();
    ShadowInstrumentation shadowInstrumentation = Shadow.extract(activityThread.getInstrumentation());
    return shadowInstrumentation.getNextStartedActivityForResult();
  }

  /**
   * Returns the most recent {@code Intent} started by
   * {@link Activity#startActivityForResult(Intent, int)} without consuming it.
   *
   * @return the most recently started {@code Intent}, wrapped in
   *         an {@link ShadowActivity.IntentForResult} object
   */
  public IntentForResult peekNextStartedActivityForResult() {
    ActivityThread activityThread = (ActivityThread) RuntimeEnvironment.getActivityThread();
    ShadowInstrumentation shadowInstrumentation = Shadow.extract(activityThread.getInstrumentation());
    return shadowInstrumentation.peekNextStartedActivityForResult();
  }

  @Implementation
  protected Object getLastNonConfigurationInstance() {
    if (lastNonConfigurationInstance != null) {
      return lastNonConfigurationInstance;
    }
    return directlyOn(realActivity, Activity.class).getLastNonConfigurationInstance();
  }

  /** @deprecated use {@link ActivityController#recreate()}. */
  @Deprecated
  public void setLastNonConfigurationInstance(Object lastNonConfigurationInstance) {
    this.lastNonConfigurationInstance = lastNonConfigurationInstance;
  }

  /**
   * @param view View to focus.
   */
  public void setCurrentFocus(View view) {
    currentFocus = view;
  }

  @Implementation
  protected View getCurrentFocus() {
    return currentFocus;
  }

  public int getPendingTransitionEnterAnimationResourceId() {
    return pendingTransitionEnterAnimResId;
  }

  public int getPendingTransitionExitAnimationResourceId() {
    return pendingTransitionExitAnimResId;
  }

  @Implementation
  protected boolean onCreateOptionsMenu(Menu menu) {
    optionsMenu = menu;
    return directlyOn(realActivity, Activity.class).onCreateOptionsMenu(menu);
  }

  /**
   * Return the options menu.
   *
   * @return  Options menu.
   */
  public Menu getOptionsMenu() {
    return optionsMenu;
  }

  /**
   * Perform a click on a menu item.
   *
   * @param menuItemResId Menu item resource ID.
   * @return True if the click was handled, false otherwise.
   */
  public boolean clickMenuItem(int menuItemResId) {
    final RoboMenuItem item = new RoboMenuItem(menuItemResId);
    return realActivity.onMenuItemSelected(Window.FEATURE_OPTIONS_PANEL, item);
  }

  /** For internal use only. Not for public use. */
  public void callOnActivityResult(int requestCode, int resultCode, Intent resultData) {
    final ActivityInvoker invoker = new ActivityInvoker();
    invoker
        .call("onActivityResult", Integer.TYPE, Integer.TYPE, Intent.class)
        .with(requestCode, resultCode, resultData);
  }

  /** For internal use only. Not for public use. */
  public <T extends Activity> void attachController(ActivityController controller) {
    this.controller = controller;
  }

  /** Sets if startIntentSenderForRequestCode will throw an IntentSender.SendIntentException. */
  public void setThrowIntentSenderException(boolean throwIntentSenderException) {
    this.throwIntentSenderException = throwIntentSenderException;
  }

  /**
   * Container object to hold an Intent, together with the requestCode used
   * in a call to {@code Activity.startActivityForResult(Intent, int)}
   */
  public static class IntentForResult {
    public Intent intent;
    public int requestCode;
    public Bundle options;

    public IntentForResult(Intent intent, int requestCode) {
      this.intent = intent;
      this.requestCode = requestCode;
      this.options = null;
    }

    public IntentForResult(Intent intent, int requestCode, Bundle options) {
      this.intent = intent;
      this.requestCode = requestCode;
      this.options = options;
    }
  }

  public void receiveResult(Intent requestIntent, int resultCode, Intent resultIntent) {
    ActivityThread activityThread = (ActivityThread) RuntimeEnvironment.getActivityThread();
    ShadowInstrumentation shadowInstrumentation = Shadow.extract(activityThread.getInstrumentation());
    int requestCode = shadowInstrumentation.getRequestCodeForIntent(requestIntent);

    callOnActivityResult(requestCode, resultCode, resultIntent);
  }

  @Implementation
  protected final void showDialog(int id) {
    showDialog(id, null);
  }

  @Implementation
  protected final void dismissDialog(int id) {
    final Dialog dialog = dialogForId.get(id);
    if (dialog == null) {
      throw new IllegalArgumentException();
    }

    dialog.dismiss();
  }

  @Implementation
  protected final void removeDialog(int id) {
    dialogForId.remove(id);
  }

  @Implementation
  protected final boolean showDialog(int id, Bundle bundle) {
    this.lastShownDialogId = id;
    Dialog dialog = dialogForId.get(id);

    if (dialog == null) {
      final ActivityInvoker invoker = new ActivityInvoker();
      dialog = (Dialog) invoker.call("onCreateDialog", Integer.TYPE).with(id);
      if (dialog == null) {
        return false;
      }
      if (bundle == null) {
        invoker.call("onPrepareDialog", Integer.TYPE, Dialog.class).with(id, dialog);
      } else {
        invoker.call("onPrepareDialog", Integer.TYPE, Dialog.class, Bundle.class).with(id, dialog, bundle);
      }

      dialogForId.put(id, dialog);
    }

    dialog.show();
    return true;
  }

  public void setIsTaskRoot(boolean isRoot) {
    mIsTaskRoot = isRoot;
  }

  @Implementation
  protected final boolean isTaskRoot() {
    return mIsTaskRoot;
  }

  /**
   * @return the dialog resource id passed into
   *         {@code Activity.showDialog(int, Bundle)} or {@code Activity.showDialog(int)}
   */
  public Integer getLastShownDialogId() {
    return lastShownDialogId;
  }

  public boolean hasCancelledPendingTransitions() {
    return pendingTransitionEnterAnimResId == 0 && pendingTransitionExitAnimResId == 0;
  }

  @Implementation
  protected void overridePendingTransition(int enterAnim, int exitAnim) {
    pendingTransitionEnterAnimResId = enterAnim;
    pendingTransitionExitAnimResId = exitAnim;
  }

  public Dialog getDialogById(int dialogId) {
    return dialogForId.get(dialogId);
  }

  @Implementation
  protected void recreate() {
    if (controller != null) {
      controller.recreate();
    } else {
      throw new IllegalStateException(
          "Cannot use an Activity that is not managed by an ActivityController");
    }
  }

  @Implementation
  protected void startManagingCursor(Cursor c) {
    managedCursors.add(c);
  }

  @Implementation
  protected void stopManagingCursor(Cursor c) {
    managedCursors.remove(c);
  }

  public List<Cursor> getManagedCursors() {
    return managedCursors;
  }

  @Implementation
  protected final void setVolumeControlStream(int streamType) {
    this.streamType = streamType;
  }

  @Implementation
  protected final int getVolumeControlStream() {
    return streamType;
  }

  @Implementation(minSdk = M)
  protected final void requestPermissions(String[] permissions, int requestCode) {
    lastRequestedPermission = new PermissionsRequest(permissions, requestCode);
  }

  /**
   * Starts a lock task.
   *
   * <p>The status of the lock task can be verified using {@link #isLockTask} method. Otherwise this
   * implementation has no effect.
   */
  @Implementation(minSdk = LOLLIPOP)
  protected void startLockTask() {
    Shadow.<ShadowActivityManager>extract(getActivityManager())
        .setLockTaskModeState(ActivityManager.LOCK_TASK_MODE_LOCKED);
  }

  /**
   * Stops a lock task.
   *
   * <p>The status of the lock task can be verified using {@link #isLockTask} method. Otherwise this
   * implementation has no effect.
   */
  @Implementation(minSdk = LOLLIPOP)
  protected void stopLockTask() {
    Shadow.<ShadowActivityManager>extract(getActivityManager())
        .setLockTaskModeState(ActivityManager.LOCK_TASK_MODE_NONE);
  }

  /**
   * Returns if the activity is in the lock task mode.
   *
   * @deprecated Use {@link ActivityManager#getLockTaskModeState} instead.
   */
  @Deprecated
  public boolean isLockTask() {
    return getActivityManager().isInLockTaskMode();
  }

  private ActivityManager getActivityManager() {
    return (ActivityManager) realActivity.getSystemService(Context.ACTIVITY_SERVICE);
  }

  /**
   * Changes state of {@link #isInMultiWindowMode} method.
   */
  public void setInMultiWindowMode(boolean value) {
    inMultiWindowMode = value;
  }

  @Implementation(minSdk = N)
  protected boolean isInMultiWindowMode() {
    return inMultiWindowMode;
  }

  /**
   * Gets the last startIntentSenderForResult request made to this activity.
   *
   * @return The IntentSender request details.
   */
  public IntentSenderRequest getLastIntentSenderRequest() {
    return lastIntentSenderRequest;
  }

  /**
   * Gets the last permission request submitted to this activity.
   *
   * @return The permission request details.
   */
  public PermissionsRequest getLastRequestedPermission() {
    return lastRequestedPermission;
  }

  private final class ActivityInvoker {
    private Method method;

    public ActivityInvoker call(final String methodName, final Class... argumentClasses) {
      try {
        method = Activity.class.getDeclaredMethod(methodName, argumentClasses);
        method.setAccessible(true);
        return this;
      } catch (NoSuchMethodException e) {
        throw new RuntimeException(e);
      }
    }

    public Object withNothing() {
      return with();
    }

    public Object with(final Object... parameters) {
      try {
        return method.invoke(realActivity, parameters);
      } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
        throw new RuntimeException(e);
      }
    }
  }

  /** Class to hold a permissions request, including its request code. */
  public static class PermissionsRequest {
    public final int requestCode;
    public final String[] requestedPermissions;

    public PermissionsRequest(String[] requestedPermissions, int requestCode) {
      this.requestedPermissions = requestedPermissions;
      this.requestCode = requestCode;
    }
  }

  /** Class to holds details of a startIntentSenderForResult request. */
  public static class IntentSenderRequest {
    public final IntentSender intentSender;
    public final int requestCode;
    @Nullable public final Intent fillInIntent;
    public final int flagsMask;
    public final int flagsValues;
    public final int extraFlags;
    public final Bundle options;

    public IntentSenderRequest(
        IntentSender intentSender,
        int requestCode,
        @Nullable Intent fillInIntent,
        int flagsMask,
        int flagsValues,
        int extraFlags,
        Bundle options) {
      this.intentSender = intentSender;
      this.requestCode = requestCode;
      this.fillInIntent = fillInIntent;
      this.flagsMask = flagsMask;
      this.flagsValues = flagsValues;
      this.extraFlags = extraFlags;
      this.options = options;
    }
  }

  private ShadowPackageManager shadowOf(PackageManager packageManager) {
    return Shadow.extract(packageManager);
  }
}
