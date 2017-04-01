/**
 * Copyright (c) 2014-present, Facebook, Inc.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */

package com.facebook.litho;

import android.os.Looper;

import com.facebook.litho.testing.testrunner.ComponentsTestRunner;
import com.facebook.litho.testing.TestDrawableComponent;
import com.facebook.litho.testing.TestLayoutComponent;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.reflect.Whitebox;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowLooper;

import static com.facebook.litho.SizeSpec.AT_MOST;
import static com.facebook.litho.SizeSpec.EXACTLY;
import static com.facebook.litho.SizeSpec.makeSizeSpec;
import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertNull;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.fail;

@RunWith(ComponentsTestRunner.class)
public class ComponentTreeTest {

  private int mWidthSpec;
  private int mWidthSpec2;
  private int mHeightSpec;
  private int mHeightSpec2;

  private Component mComponent;
  private ShadowLooper mLayoutThreadShadowLooper;
  private ComponentContext mContext;

  private static class TestComponent<L extends ComponentLifecycle> extends Component<L> {
    public TestComponent(L component) {
      super(component);
    }

    @Override
    public String getSimpleName() {
      return "TestComponent";
    }
  }

  @Before
  public void setup() throws Exception {
    mContext = new ComponentContext(RuntimeEnvironment.application);
    mComponent = TestDrawableComponent.create(mContext)
        .build();

    mLayoutThreadShadowLooper = Shadows.shadowOf(
        (Looper) Whitebox.invokeMethod(
            ComponentTree.class,
            "getDefaultLayoutThreadLooper"));

    mWidthSpec = makeSizeSpec(39, EXACTLY);
    mWidthSpec2 = makeSizeSpec(40, EXACTLY);
    mHeightSpec = makeSizeSpec(41, EXACTLY);
    mHeightSpec2 = makeSizeSpec(42, EXACTLY);
  }

  private void creationCommonChecks(ComponentTree componentTree) {
    // Not view or attached yet
    Assert.assertNull(getComponentView(componentTree));
    Assert.assertFalse(isAttached(componentTree));

    // No measure spec from view yet.
    Assert.assertFalse(
        (Boolean) Whitebox.getInternalState(componentTree, "mHasViewMeasureSpec"));

    // The component input should be the one we passed in
    Assert.assertSame(
        mComponent,
        Whitebox.getInternalState(componentTree, "mRoot"));
  }

  private void postSizeSpecChecks(
      ComponentTree componentTree,
      String layoutStateVariableName) {
    postSizeSpecChecks(
        componentTree,
        layoutStateVariableName,
        mWidthSpec,
        mHeightSpec);
  }

  private void postSizeSpecChecks(
      ComponentTree componentTree,
      String layoutStateVariableName,
      int widthSpec,
      int heightSpec) {
    // Spec specified in create

    Assert.assertTrue(componentTreeHasSizeSpec(componentTree));
    assertEquals(
        widthSpec,
        Whitebox.getInternalState(componentTree, "mWidthSpec"));

    assertEquals(
        heightSpec,
        Whitebox.getInternalState(componentTree, "mHeightSpec"));

    LayoutState mainThreadLayoutState = Whitebox.getInternalState(
        componentTree, "mMainThreadLayoutState");

    LayoutState backgroundLayoutState = Whitebox.getInternalState(
        componentTree, "mBackgroundLayoutState");

    LayoutState layoutState = null;
    LayoutState nullLayoutState = null;
    if ("mMainThreadLayoutState".equals(layoutStateVariableName)) {
      layoutState = mainThreadLayoutState;
      nullLayoutState = backgroundLayoutState;
    } else if ("mBackgroundLayoutState".equals(layoutStateVariableName)) {
      layoutState = backgroundLayoutState;
      nullLayoutState = mainThreadLayoutState;
    } else {
      fail("Incorrect variable name: " + layoutStateVariableName);
    }

    Assert.assertNull(nullLayoutState);
    Assert.assertTrue(
        layoutState.isCompatibleComponentAndSpec(
            mComponent.getId(),
            widthSpec,
            heightSpec));
  }

  @Test
  public void testCreate() {
    ComponentTree componentTree =
        ComponentTree.create(mContext, mComponent)
            .incrementalMount(false)
            .layoutDiffing(false)
            .build();

    creationCommonChecks(componentTree);

    // Both the main thread and the background layout state shouldn't be calculated yet.
    Assert.assertNull(Whitebox.getInternalState(componentTree, "mMainThreadLayoutState"));
    Assert.assertNull(Whitebox.getInternalState(componentTree, "mBackgroundLayoutState"));

    Assert.assertFalse(componentTreeHasSizeSpec(componentTree));
  }

  @Test
  public void testSetSizeSpec() {
    ComponentTree componentTree =
        ComponentTree.create(mContext, mComponent)
            .incrementalMount(false)
            .layoutDiffing(false)
            .build();
    componentTree.setSizeSpec(mWidthSpec, mHeightSpec);

    // Since this happens post creation, it's not in general safe to update the main thread layout
    // state synchronously, so the result should be in the background layout state
    postSizeSpecChecks(componentTree, "mBackgroundLayoutState");
  }

  @Test
  public void testSetSizeSpecAsync() {
    ComponentTree componentTree =
        ComponentTree.create(mContext, mComponent)
            .incrementalMount(false)
            .layoutDiffing(false)
            .build();
    componentTree.setSizeSpecAsync(mWidthSpec, mHeightSpec);

    // Only fields changed but no layout is done yet.

    Assert.assertTrue(componentTreeHasSizeSpec(componentTree));
    assertEquals(
        mWidthSpec,
        Whitebox.getInternalState(componentTree, "mWidthSpec"));
    assertEquals(
        mHeightSpec,
        Whitebox.getInternalState(componentTree, "mHeightSpec"));
    Assert.assertNull(Whitebox.getInternalState(componentTree, "mMainThreadLayoutState"));
    Assert.assertNull(Whitebox.getInternalState(componentTree, "mBackgroundLayoutState"));

    // Now the background thread run the queued task.
    mLayoutThreadShadowLooper.runOneTask();

    // Since this happens post creation, it's not in general safe to update the main thread layout
    // state synchronously, so the result should be in the background layout state
    postSizeSpecChecks(componentTree, "mBackgroundLayoutState");
  }

  @Test
  public void testSetSizeSpecAsyncThenSyncBeforeRunningTask() {
    ComponentTree componentTree =
        ComponentTree.create(mContext, mComponent)
            .incrementalMount(false)
            .layoutDiffing(false)
            .build();

    componentTree.setSizeSpecAsync(mWidthSpec, mHeightSpec);
    componentTree.setSizeSpec(mWidthSpec2, mHeightSpec2);

    mLayoutThreadShadowLooper.runToEndOfTasks();

    // Since this happens post creation, it's not in general safe to update the main thread layout
    // state synchronously, so the result should be in the background layout state
    postSizeSpecChecks(
        componentTree,
        "mBackgroundLayoutState",
        mWidthSpec2,
        mHeightSpec2);
  }

  @Test
  public void testSetSizeSpecAsyncThenSyncAfterRunningTask() {
    ComponentTree componentTree =
        ComponentTree.create(mContext, mComponent)
            .incrementalMount(false)
            .layoutDiffing(false)
            .build();
    componentTree.setSizeSpecAsync(mWidthSpec, mHeightSpec);

    mLayoutThreadShadowLooper.runToEndOfTasks();

    componentTree.setSizeSpec(mWidthSpec2, mHeightSpec2);

    // Since this happens post creation, it's not in general safe to update the main thread layout
    // state synchronously, so the result should be in the background layout state
    postSizeSpecChecks(
        componentTree,
        "mBackgroundLayoutState",
        mWidthSpec2,
        mHeightSpec2);
  }

  @Test
  public void testSetSizeSpecWithOutput() {
    ComponentTree componentTree =
        ComponentTree.create(mContext, mComponent)
            .incrementalMount(false)
            .layoutDiffing(false)
            .build();

    Size size = new Size();

    componentTree.setSizeSpec(mWidthSpec, mHeightSpec, size);

    assertEquals(SizeSpec.getSize(mWidthSpec), size.width, 0.0);
    assertEquals(SizeSpec.getSize(mHeightSpec), size.height, 0.0);

    // Since this happens post creation, it's not in general safe to update the main thread layout
    // state synchronously, so the result should be in the background layout state
    postSizeSpecChecks(componentTree, "mBackgroundLayoutState");
  }

  @Test
  public void testSetCompatibleSizeSpec() {
    ComponentTree componentTree =
        ComponentTree.create(mContext, mComponent)
            .incrementalMount(false)
            .layoutDiffing(false)
            .build();

    Size size = new Size();

    componentTree.setSizeSpec(
        SizeSpec.makeSizeSpec(100, AT_MOST),
        SizeSpec.makeSizeSpec(100, AT_MOST),
        size);

    assertEquals(100, size.width, 0.0);
    assertEquals(100, size.height, 0.0);

    LayoutState firstLayoutState = componentTree.getBackgroundLayoutState();
    assertNotNull(firstLayoutState);

    componentTree.setSizeSpec(
        SizeSpec.makeSizeSpec(100, EXACTLY),
        SizeSpec.makeSizeSpec(100, EXACTLY),
        size);

    assertEquals(100, size.width, 0.0);
    assertEquals(100, size.height, 0.0);

    assertEquals(firstLayoutState, componentTree.getBackgroundLayoutState());
  }

  @Test
  public void testSetCompatibleSizeSpecWithDifferentRoot() {
    ComponentTree componentTree =
        ComponentTree.create(mContext, mComponent)
            .incrementalMount(false)
            .layoutDiffing(false)
            .build();

    Size size = new Size();

    componentTree.setSizeSpec(
        SizeSpec.makeSizeSpec(100, AT_MOST),
        SizeSpec.makeSizeSpec(100, AT_MOST),
        size);

    assertEquals(100, size.width, 0.0);
    assertEquals(100, size.height, 0.0);

    LayoutState firstLayoutState = componentTree.getBackgroundLayoutState();
    assertNotNull(firstLayoutState);

    componentTree.setRootAndSizeSpec(
        TestDrawableComponent.create(mContext).build(),
        SizeSpec.makeSizeSpec(100, EXACTLY),
        SizeSpec.makeSizeSpec(100, EXACTLY),
        size);

    assertNotEquals(firstLayoutState, componentTree.getBackgroundLayoutState());
  }

  @Test
  public void testSetInput() {
    Component component = TestLayoutComponent.create(mContext)
        .build();

    ComponentTree componentTree =
        ComponentTree.create(mContext, component)
            .incrementalMount(false)
            .layoutDiffing(false)
            .build();

    componentTree.setRoot(mComponent);

    creationCommonChecks(componentTree);
    Assert.assertNull(Whitebox.getInternalState(componentTree, "mMainThreadLayoutState"));
    Assert.assertNull(Whitebox.getInternalState(componentTree, "mBackgroundLayoutState"));

    componentTree.setSizeSpec(mWidthSpec, mHeightSpec);

    // Since this happens post creation, it's not in general safe to update the main thread layout
    // state synchronously, so the result should be in the background layout state
    postSizeSpecChecks(componentTree, "mBackgroundLayoutState");
  }

  @Test
  public void testSetComponentFromView() {
    Component component1 = TestDrawableComponent.create(mContext)
        .build();
    ComponentTree componentTree1 = ComponentTree.create(
        mContext,
        component1)
        .incrementalMount(false)
        .layoutDiffing(false)
        .build();

    Component component2 = TestDrawableComponent.create(mContext)
        .build();
    ComponentTree componentTree2 = ComponentTree.create(
        mContext,
        component2)
        .incrementalMount(false)
        .layoutDiffing(false)
        .build();

    Assert.assertNull(getComponentView(componentTree1));
    Assert.assertNull(getComponentView(componentTree2));

    ComponentView componentView = new ComponentView(mContext);
    componentView.setComponent(componentTree1);

    Assert.assertNotNull(getComponentView(componentTree1));
    Assert.assertNull(getComponentView(componentTree2));

    componentView.setComponent(componentTree2);

    Assert.assertNull(getComponentView(componentTree1));
    Assert.assertNotNull(getComponentView(componentTree2));
  }

  @Test
  public void testComponentTreeReleaseClearsView() {
    Component component = TestDrawableComponent.create(mContext)
        .build();
    ComponentTree componentTree = ComponentTree.create(
        mContext,
        component)
        .incrementalMount(false)
        .layoutDiffing(false)
        .build();

    ComponentView componentView = new ComponentView(mContext);
    componentView.setComponent(componentTree);

    assertEquals(componentView.getComponent(), componentTree);

    componentTree.release();

    assertNull(componentView.getComponent());
  }

  @Test
  public void testsetTreeToTwoViewsBothAttached() {
    Component component = TestDrawableComponent.create(mContext)
        .build();

    ComponentTree componentTree = ComponentTree.create(
        mContext,
        component)
        .incrementalMount(false)
        .layoutDiffing(false)
        .build();

    // Attach first view.
    ComponentView componentView1 = new ComponentView(mContext);
    componentView1.setComponent(componentTree);
    componentView1.onAttachedToWindow();

    // Attach second view.
    ComponentView componentView2 = new ComponentView(mContext);
    componentView2.onAttachedToWindow();

    // Set the component that is already mounted on the first view, on the second attached view.
    // This should be ok.
    componentView2.setComponent(componentTree);
  }

  @Test
  public void testSettingNewViewToTree() {
    Component component = TestDrawableComponent.create(mContext)
        .build();

    ComponentTree componentTree = ComponentTree.create(
        mContext,
        component)
        .incrementalMount(false)
        .layoutDiffing(false)
        .build();

    // Attach first view.
    ComponentView componentView1 = new ComponentView(mContext);
    componentView1.setComponent(componentTree);

    assertEquals(componentView1, getComponentView(componentTree));
    assertEquals(componentTree, getComponentTree(componentView1));

    // Attach second view.
    ComponentView componentView2 = new ComponentView(mContext);

    Assert.assertNull(getComponentTree(componentView2));

    componentView2.setComponent(componentTree);

    assertEquals(componentView2, getComponentView(componentTree));
    assertEquals(componentTree, getComponentTree(componentView2));

    Assert.assertNull(getComponentTree(componentView1));
  }

  private static ComponentView getComponentView(ComponentTree componentTree) {
    return Whitebox.getInternalState(componentTree, "mComponentView");
  }

  private static boolean isAttached(ComponentTree componentTree) {
    return Whitebox.getInternalState(componentTree, "mIsAttached");
  }

  private static ComponentTree getComponentTree(ComponentView componentView) {
    return Whitebox.getInternalState(componentView, "mComponent");
  }

  private static boolean componentTreeHasSizeSpec(ComponentTree componentTree) {
    try {
      boolean hasCssSpec;
      // Need to hold the lock on componentTree here otherwise the invocation of hasCssSpec
      // will fail.
      synchronized (componentTree) {
        hasCssSpec = Whitebox.invokeMethod(componentTree, ComponentTree.class, "hasSizeSpec");
      }
      return hasCssSpec;
    } catch (Exception e) {
      throw new IllegalArgumentException("Failed to invoke hasSizeSpec on ComponentTree for: "+e);
    }
  }
}
