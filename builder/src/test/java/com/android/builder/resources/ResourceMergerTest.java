/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.builder.resources;

import com.android.builder.TestUtils;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.google.common.base.Charsets;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Maps;
import com.google.common.io.Files;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class ResourceMergerTest extends BaseTestCase {

    private static ResourceMerger sResourceMerger = null;

    public void testMergeByCount() throws Exception {
        ResourceMerger merger = getResourceMerger();

        assertEquals(25, merger.size());
    }

    public void testMergedResourcesByName() throws Exception {
        ResourceMerger merger = getResourceMerger();

        verifyResourceExists(merger,
                "drawable/icon",
                "drawable-ldpi/icon",
                "drawable/icon2",
                "raw/foo",
                "layout/main",
                "layout/layout_ref",
                "layout/alias_replaced_by_file",
                "layout/file_replaced_by_alias",
                "drawable/color_drawable",
                "drawable/drawable_ref",
                "color/color",
                "string/basic_string",
                "string/xliff_string",
                "string/styled_string",
                "style/style",
                "array/string_array",
                "attr/dimen_attr",
                "attr/string_attr",
                "attr/enum_attr",
                "attr/flag_attr",
                "declare-styleable/declare_styleable",
                "dimen/dimen",
                "id/item_id",
                "integer/integer"
        );
    }

    public void testReplacedLayout() throws Exception {
        ResourceMerger merger = getResourceMerger();
        ListMultimap<String, Resource> mergedMap = merger.getResourceMap();

        List<Resource> values = mergedMap.get("layout/main");

        // the overlay means there's 2 versions of this resource.
        assertEquals(2, values.size());
        Resource mainLayout = values.get(1);

        ResourceFile sourceFile = mainLayout.getSource();
        assertTrue(sourceFile.getFile().getAbsolutePath().endsWith("overlay/layout/main.xml"));
    }

    public void testReplacedAlias() throws Exception {
        ResourceMerger merger = getResourceMerger();
        ListMultimap<String, Resource> mergedMap = merger.getResourceMap();


        List<Resource> values = mergedMap.get("layout/alias_replaced_by_file");

        // the overlay means there's 2 versions of this resource.
        assertEquals(2, values.size());
        Resource layout = values.get(1);

        // since it's replaced by a file, there's no node.
        assertNull(layout.getValue());
    }

    public void testReplacedFile() throws Exception {
        ResourceMerger merger = getResourceMerger();
        ListMultimap<String, Resource> mergedMap = merger.getResourceMap();

        List<Resource> values = mergedMap.get("layout/file_replaced_by_alias");

        // the overlay means there's 2 versions of this resource.
        assertEquals(2, values.size());
        Resource layout = values.get(1);

        // since it's replaced by a file, there's no node.
        assertNotNull(layout.getValue());
    }

    public void testMergeWrite() throws Exception {
        ResourceMerger merger = getResourceMerger();

        File folder = getWrittenResources();

        ResourceSet writtenSet = new ResourceSet("unused");
        writtenSet.addSource(folder);
        writtenSet.loadFromFiles();

        // compare the two maps, but not using the full map as the set loaded from the output
        // won't contains all versions of each Resource item.
        compareResourceMaps(merger, writtenSet, false /*full compare*/);
    }

    public void testMergeBlob() throws Exception {
        ResourceMerger merger = getResourceMerger();

        File folder = Files.createTempDir();
        merger.writeBlobTo(folder);

        ResourceMerger loadedMerger = new ResourceMerger();
        loadedMerger.loadFromBlob(folder);

        compareResourceMaps(merger, loadedMerger, true /*full compare*/);
    }

    /**
     * Tests the path replacement in the merger.xml file loaded from testData/
     * @throws Exception
     */
    public void testLoadingTestPathReplacement() throws Exception {
        File root = TestUtils.getRoot("baseMerge");
        File fakeRoot = getMergedBlobFolder(root);

        ResourceMerger resourceMerger = new ResourceMerger();
        resourceMerger.loadFromBlob(fakeRoot);

        List<ResourceSet> sets = resourceMerger.getResourceSets();
        for (ResourceSet set : sets) {
            List<File> sourceFiles = set.getSourceFiles();

            // there should only be one
            assertEquals(1, sourceFiles.size());

            File sourceFile = sourceFiles.get(0);
            assertTrue(String.format("File %s is located in %s", sourceFile, root),
                    sourceFile.getAbsolutePath().startsWith(root.getAbsolutePath()));
        }
    }

    public void testUpdateWithBasicFiles() throws Exception {
        File root = getIncMergeRoot("basicFiles");
        File fakeRoot = getMergedBlobFolder(root);
        ResourceMerger resourceMerger = new ResourceMerger();
        resourceMerger.loadFromBlob(fakeRoot);

        List<ResourceSet> sets = resourceMerger.getResourceSets();
        assertEquals(2, sets.size());

        // ----------------
        // first set is the main one, no change here
        ResourceSet mainSet = sets.get(0);
        File mainBase = new File(root, "main");
        File mainDrawable = new File(mainBase, "drawable");
        File mainDrawableLdpi = new File(mainBase, "drawable-ldpi");

        // touched/removed files:
        File mainDrawableTouched = new File(mainDrawable, "touched.png");
        mainSet.updateWith(mainBase, mainDrawableTouched, FileStatus.CHANGED);

        File mainDrawableRemoved = new File(mainDrawable, "removed.png");
        mainSet.updateWith(mainBase, mainDrawableRemoved, FileStatus.REMOVED);

        File mainDrawableLdpiRemoved = new File(mainDrawableLdpi, "removed.png");
        mainSet.updateWith(mainBase, mainDrawableLdpiRemoved, FileStatus.REMOVED);

        // ----------------
        // second set is the overlay one
        ResourceSet overlaySet = sets.get(1);
        File overlayBase = new File(root, "overlay");
        File overlayDrawable = new File(overlayBase, "drawable");
        File overlayDrawableHdpi = new File(overlayBase, "drawable-hdpi");

        // new/removed files:
        File overlayDrawableNewOverlay = new File(overlayDrawable, "new_overlay.png");
        overlaySet.updateWith(overlayBase, overlayDrawableNewOverlay, FileStatus.NEW);

        File overlayDrawableRemovedOverlay = new File(overlayDrawable, "removed_overlay.png");
        overlaySet.updateWith(overlayBase, overlayDrawableRemovedOverlay, FileStatus.REMOVED);

        File overlayDrawableHdpiNewAlternate = new File(overlayDrawableHdpi, "new_alternate.png");
        overlaySet.updateWith(overlayBase, overlayDrawableHdpiNewAlternate, FileStatus.NEW);

        // validate for duplicates
        resourceMerger.validateResourceSets();

        // check the content.
        ListMultimap<String, Resource> mergedMap = resourceMerger.getResourceMap();

        // check unchanged file is WRITTEN
        List<Resource> drawableUntouched = mergedMap.get("drawable/untouched");
        assertEquals(1, drawableUntouched.size());
        assertTrue(drawableUntouched.get(0).isWritten());
        assertFalse(drawableUntouched.get(0).isTouched());
        assertFalse(drawableUntouched.get(0).isRemoved());

        // check replaced file is TOUCHED
        List<Resource> drawableTouched = mergedMap.get("drawable/touched");
        assertEquals(1, drawableTouched.size());
        assertTrue(drawableTouched.get(0).isWritten());
        assertTrue(drawableTouched.get(0).isTouched());
        assertFalse(drawableTouched.get(0).isRemoved());

        // check removed file is REMOVED
        List<Resource> drawableRemoved = mergedMap.get("drawable/removed");
        assertEquals(1, drawableRemoved.size());
        assertTrue(drawableRemoved.get(0).isWritten());
        assertTrue(drawableRemoved.get(0).isRemoved());

        // check new overlay: two objects, last one is TOUCHED
        List<Resource> drawableNewOverlay = mergedMap.get("drawable/new_overlay");
        assertEquals(2, drawableNewOverlay.size());
        Resource newOverlay = drawableNewOverlay.get(1);
        assertEquals(overlayDrawableNewOverlay, newOverlay.getSource().getFile());
        assertFalse(newOverlay.isWritten());
        assertTrue(newOverlay.isTouched());

        // check new alternate: one objects, last one is TOUCHED
        List<Resource> drawableHdpiNewAlternate = mergedMap.get("drawable-hdpi/new_alternate");
        assertEquals(1, drawableHdpiNewAlternate.size());
        Resource newAlternate = drawableHdpiNewAlternate.get(0);
        assertEquals(overlayDrawableHdpiNewAlternate, newAlternate.getSource().getFile());
        assertFalse(newAlternate.isWritten());
        assertTrue(newAlternate.isTouched());

        // write and check the result of writeResourceFolder
        // copy the current resOut which serves as pre incremental update state.
        File resFolder = getFolderCopy(new File(root, "resOut"));

        // write the content of the resource merger.
        resourceMerger.writeResourceFolder(resFolder);

        // Check the content.
        checkImageColor(new File(resFolder, "drawable" + File.separator + "touched.png"),
                (int) 0xFF00FF00);
        checkImageColor(new File(resFolder, "drawable" + File.separator + "untouched.png"),
                (int) 0xFF00FF00);
        checkImageColor(new File(resFolder, "drawable" + File.separator + "new_overlay.png"),
                (int) 0xFF00FF00);
        checkImageColor(new File(resFolder, "drawable" + File.separator + "removed_overlay.png"),
                (int) 0xFF00FF00);
        checkImageColor(new File(resFolder, "drawable-hdpi" + File.separator + "new_alternate.png"),
                (int) 0xFF00FF00);
        assertFalse(new File(resFolder, "drawable-ldpi" + File.separator + "removed.png").isFile());
    }

    public void testUpdateWithBasicValues() throws Exception {
        File root = getIncMergeRoot("basicValues");
        File fakeRoot = getMergedBlobFolder(root);
        ResourceMerger resourceMerger = new ResourceMerger();
        resourceMerger.loadFromBlob(fakeRoot);

        List<ResourceSet> sets = resourceMerger.getResourceSets();
        assertEquals(2, sets.size());

        // ----------------
        // first set is the main one, no change here
        ResourceSet mainSet = sets.get(0);
        File mainBase = new File(root, "main");
        File mainValues = new File(mainBase, "values");
        File mainValuesEn = new File(mainBase, "values-en");

        // touched file:
        File mainValuesTouched = new File(mainValues, "values.xml");
        mainSet.updateWith(mainBase, mainValuesTouched, FileStatus.CHANGED);

        // removed files
        File mainValuesEnRemoved = new File(mainValuesEn, "values.xml");
        mainSet.updateWith(mainBase, mainValuesEnRemoved, FileStatus.REMOVED);

        // ----------------
        // second set is the overlay one
        ResourceSet overlaySet = sets.get(1);
        File overlayBase = new File(root, "overlay");
        File overlayValues = new File(overlayBase, "values");
        File overlayValuesFr = new File(overlayBase, "values-fr");

        // new files:
        File overlayValuesNew = new File(overlayValues, "values.xml");
        overlaySet.updateWith(overlayBase, overlayValuesNew, FileStatus.NEW);
        File overlayValuesFrNew = new File(overlayValuesFr, "values.xml");
        overlaySet.updateWith(overlayBase, overlayValuesFrNew, FileStatus.NEW);

        // validate for duplicates
        resourceMerger.validateResourceSets();

        // check the content.
        ListMultimap<String, Resource> mergedMap = resourceMerger.getResourceMap();

        // check unchanged string is WRITTEN
        List<Resource> valuesUntouched = mergedMap.get("string/untouched");
        assertEquals(1, valuesUntouched.size());
        assertTrue(valuesUntouched.get(0).isWritten());
        assertFalse(valuesUntouched.get(0).isTouched());
        assertFalse(valuesUntouched.get(0).isRemoved());

        // check replaced file is TOUCHED
        List<Resource> valuesTouched = mergedMap.get("string/touched");
        assertEquals(1, valuesTouched.size());
        assertTrue(valuesTouched.get(0).isWritten());
        assertTrue(valuesTouched.get(0).isTouched());
        assertFalse(valuesTouched.get(0).isRemoved());

        // check removed file is REMOVED
        List<Resource> valuesRemoved = mergedMap.get("string/removed");
        assertEquals(1, valuesRemoved.size());
        assertTrue(valuesRemoved.get(0).isWritten());
        assertTrue(valuesRemoved.get(0).isRemoved());

        valuesRemoved = mergedMap.get("string-en/removed");
        assertEquals(1, valuesRemoved.size());
        assertTrue(valuesRemoved.get(0).isWritten());
        assertTrue(valuesRemoved.get(0).isRemoved());

        // check new overlay: two objects, last one is TOUCHED
        List<Resource> valuesNewOverlay = mergedMap.get("string/new_overlay");
        assertEquals(2, valuesNewOverlay.size());
        Resource newOverlay = valuesNewOverlay.get(1);
        assertFalse(newOverlay.isWritten());
        assertTrue(newOverlay.isTouched());

        // check new alternate: one objects, last one is TOUCHED
        List<Resource> valuesFrNewAlternate = mergedMap.get("string-fr/new_alternate");
        assertEquals(1, valuesFrNewAlternate.size());
        Resource newAlternate = valuesFrNewAlternate.get(0);
        assertFalse(newAlternate.isWritten());
        assertTrue(newAlternate.isTouched());

        // write and check the result of writeResourceFolder
        // copy the current resOut which serves as pre incremental update state.
        File resFolder = getFolderCopy(new File(root, "resOut"));

        // write the content of the resource merger.
        resourceMerger.writeResourceFolder(resFolder);

        // Check the content.
        // values/values.xml
        Map<String, String> map = quickStringOnlyValueFileParser(
                new File(resFolder, "values" + File.separator + "values.xml"));
        assertEquals("untouched", map.get("untouched"));
        assertEquals("touched", map.get("touched"));
        assertEquals("new_overlay", map.get("new_overlay"));

        // values/values-fr.xml
        map = quickStringOnlyValueFileParser(
                new File(resFolder, "values-fr" + File.separator + "values.xml"));
        assertEquals("new_alternate", map.get("new_alternate"));

        // deleted values-en/values.xml
        assertFalse(new File(resFolder, "values-en" + File.separator + "values.xml").isFile());
    }

    public void testUpdateWithBasicValues2() throws Exception {
        File root = getIncMergeRoot("basicValues2");
        File fakeRoot = getMergedBlobFolder(root);
        ResourceMerger resourceMerger = new ResourceMerger();
        resourceMerger.loadFromBlob(fakeRoot);

        List<ResourceSet> sets = resourceMerger.getResourceSets();
        assertEquals(2, sets.size());

        // ----------------
        // first set is the main one, no change here
        ResourceSet mainSet = sets.get(0);
        File mainBase = new File(root, "main");
        File mainValues = new File(mainBase, "values");

        // ----------------
        // second set is the overlay one
        ResourceSet overlaySet = sets.get(1);
        File overlayBase = new File(root, "overlay");
        File overlayValues = new File(overlayBase, "values");

        // new files:
        File overlayValuesNew = new File(overlayValues, "values.xml");
        overlaySet.updateWith(overlayBase, overlayValuesNew, FileStatus.REMOVED);

        // validate for duplicates
        resourceMerger.validateResourceSets();

        // check the content.
        ListMultimap<String, Resource> mergedMap = resourceMerger.getResourceMap();

        // check unchanged string is WRITTEN
        List<Resource> valuesUntouched = mergedMap.get("string/untouched");
        assertEquals(1, valuesUntouched.size());
        assertTrue(valuesUntouched.get(0).isWritten());
        assertFalse(valuesUntouched.get(0).isTouched());
        assertFalse(valuesUntouched.get(0).isRemoved());

        // check removed_overlay is present twice.
        List<Resource> valuesRemovedOverlay = mergedMap.get("string/removed_overlay");
        assertEquals(2, valuesRemovedOverlay.size());
        // first is untouched
        assertFalse(valuesRemovedOverlay.get(0).isWritten());
        assertFalse(valuesRemovedOverlay.get(0).isTouched());
        assertFalse(valuesRemovedOverlay.get(0).isRemoved());
        // other is removed
        assertTrue(valuesRemovedOverlay.get(1).isWritten());
        assertFalse(valuesRemovedOverlay.get(1).isTouched());
        assertTrue(valuesRemovedOverlay.get(1).isRemoved());

        // write and check the result of writeResourceFolder
        // copy the current resOut which serves as pre incremental update state.
        File resFolder = getFolderCopy(new File(root, "resOut"));

        // write the content of the resource merger.
        resourceMerger.writeResourceFolder(resFolder);

        // Check the content.
        // values/values.xml
        Map<String, String> map = quickStringOnlyValueFileParser(
                new File(resFolder, "values" + File.separator + "values.xml"));
        assertEquals("untouched", map.get("untouched"));
        assertEquals("untouched", map.get("removed_overlay"));
    }

    public void testUpdateWithFilesVsValues() throws Exception {
        File root = getIncMergeRoot("filesVsValues");
        File fakeRoot = getMergedBlobFolder(root);
        ResourceMerger resourceMerger = new ResourceMerger();
        resourceMerger.loadFromBlob(fakeRoot);

        List<ResourceSet> sets = resourceMerger.getResourceSets();
        assertEquals(1, sets.size());

        // ----------------
        // Load the main set
        ResourceSet mainSet = sets.get(0);
        File mainBase = new File(root, "main");
        File mainValues = new File(mainBase, ResourceFolderType.VALUES.getName());
        File mainLayout = new File(mainBase, ResourceFolderType.LAYOUT.getName());

        // touched file:
        File mainValuesTouched = new File(mainValues, "values.xml");
        mainSet.updateWith(mainBase, mainValuesTouched, FileStatus.CHANGED);

        // new file:
        File mainLayoutNew = new File(mainLayout, "alias_replaced_by_file.xml");
        mainSet.updateWith(mainBase, mainLayoutNew, FileStatus.NEW);

        // removed file
        File mainLayoutRemoved = new File(mainLayout, "file_replaced_by_alias.xml");
        mainSet.updateWith(mainBase, mainLayoutRemoved, FileStatus.REMOVED);

        // validate for duplicates
        resourceMerger.validateResourceSets();

        // check the content.
        ListMultimap<String, Resource> mergedMap = resourceMerger.getResourceMap();

        // check layout/main is unchanged
        List<Resource> layoutMain = mergedMap.get("layout/main");
        assertEquals(1, layoutMain.size());
        assertTrue(layoutMain.get(0).isWritten());
        assertFalse(layoutMain.get(0).isTouched());
        assertFalse(layoutMain.get(0).isRemoved());

        // check file_replaced_by_alias has 2 version, 2nd is TOUCHED, and contains a Node
        List<Resource> layoutReplacedByAlias = mergedMap.get("layout/file_replaced_by_alias");
        assertEquals(2, layoutReplacedByAlias.size());
        // 1st one is removed version, as it already existed in the item multimap
        Resource replacedByAlias = layoutReplacedByAlias.get(0);
        assertTrue(replacedByAlias.isWritten());
        assertFalse(replacedByAlias.isTouched());
        assertTrue(replacedByAlias.isRemoved());
        assertNull(replacedByAlias.getValue());
        assertEquals("file_replaced_by_alias.xml", replacedByAlias.getSource().getFile().getName());
        // 2nd version is the new one
        replacedByAlias = layoutReplacedByAlias.get(1);
        assertFalse(replacedByAlias.isWritten());
        assertTrue(replacedByAlias.isTouched());
        assertFalse(replacedByAlias.isRemoved());
        assertNotNull(replacedByAlias.getValue());
        assertEquals("values.xml", replacedByAlias.getSource().getFile().getName());

        // check alias_replaced_by_file has 2 version, 2nd is TOUCHED, and contains a Node
        List<Resource> layoutReplacedByFile = mergedMap.get("layout/alias_replaced_by_file");
        // 1st one is removed version, as it already existed in the item multimap
        assertEquals(2, layoutReplacedByFile.size());
        Resource replacedByFile = layoutReplacedByFile.get(0);
        assertTrue(replacedByFile.isWritten());
        assertFalse(replacedByFile.isTouched());
        assertTrue(replacedByFile.isRemoved());
        assertNotNull(replacedByFile.getValue());
        assertEquals("values.xml", replacedByFile.getSource().getFile().getName());
        // 2nd version is the new one
        replacedByFile = layoutReplacedByFile.get(1);
        assertFalse(replacedByFile.isWritten());
        assertTrue(replacedByFile.isTouched());
        assertFalse(replacedByFile.isRemoved());
        assertNull(replacedByFile.getValue());
        assertEquals("alias_replaced_by_file.xml", replacedByFile.getSource().getFile().getName());

        // write and check the result of writeResourceFolder
        // copy the current resOut which serves as pre incremental update state.
        File resFolder = getFolderCopy(new File(root, "resOut"));

        // write the content of the resource merger.
        resourceMerger.writeResourceFolder(resFolder);

        // deleted layout/file_replaced_by_alias.xml
        assertFalse(new File(resFolder, "layout" + File.separator + "file_replaced_by_alias.xml")
                .isFile());
        // new file layout/alias_replaced_by_file.xml
        assertTrue(new File(resFolder, "layout" + File.separator + "alias_replaced_by_file.xml")
                .isFile());

        // quick load of the values file
        File valuesFile = new File(resFolder, "values" + File.separator + "values.xml");
        assertTrue(valuesFile.isFile());
        String content = Files.toString(valuesFile, Charsets.UTF_8);
        assertTrue(content.contains("name=\"file_replaced_by_alias\""));
        assertFalse(content.contains("name=\"alias_replaced_by_file\""));
    }

    public void testCheckValidUpdate() throws Exception {
        // first merger
        ResourceMerger merger1 = createMerger(new String[][] {
                new String[] { "main",    "/main/res1", "/main/res2" },
                new String[] { "overlay", "/overlay/res1", "/overlay/res2" },
        });

        // 2nd merger with different order source files in sets.
        ResourceMerger merger2 = createMerger(new String[][] {
                new String[] { "main",    "/main/res2", "/main/res1" },
                new String[] { "overlay", "/overlay/res1", "/overlay/res2" },
        });

        assertTrue(merger1.checkValidUpdate(merger2.getResourceSets()));

        // write merger1 on disk to test writing empty ResourceSets.
        File folder = Files.createTempDir();
        merger1.writeBlobTo(folder);

        // reload it
        ResourceMerger loadedMerger = new ResourceMerger();
        loadedMerger.loadFromBlob(folder);

        assertTrue(loadedMerger.checkValidUpdate(merger1.getResourceSets()));
    }

    public void testCheckValidUpdateFail() throws Exception {
        // Test with removed overlay
        ResourceMerger merger1 = createMerger(new String[][] {
                new String[] { "main",    "/main/res1", "/main/res2" },
                new String[] { "overlay", "/overlay/res1", "/overlay/res2" },
        });

        // 2nd merger with different order source files in sets.
        ResourceMerger merger2 = createMerger(new String[][] {
                new String[] { "main",    "/main/res2", "/main/res1" },
        });

        assertFalse(merger1.checkValidUpdate(merger2.getResourceSets()));

        // Test with different overlays
        merger1 = createMerger(new String[][] {
                new String[] { "main",    "/main/res1", "/main/res2" },
                new String[] { "overlay", "/overlay/res1", "/overlay/res2" },
        });

        // 2nd merger with different order source files in sets.
        merger2 = createMerger(new String[][] {
                new String[] { "main",    "/main/res2", "/main/res1" },
                new String[] { "overlay2", "/overlay2/res1", "/overlay2/res2" },
        });

        assertFalse(merger1.checkValidUpdate(merger2.getResourceSets()));

        // Test with different overlays
        merger1 = createMerger(new String[][] {
                new String[] { "main",    "/main/res1", "/main/res2" },
                new String[] { "overlay1", "/overlay1/res1", "/overlay1/res2" },
                new String[] { "overlay2", "/overlay2/res1", "/overlay2/res2" },
        });

        // 2nd merger with different order source files in sets.
        merger2 = createMerger(new String[][] {
                new String[] { "main",    "/main/res2", "/main/res1" },
                new String[] { "overlay2", "/overlay2/res1", "/overlay2/res2" },
                new String[] { "overlay1", "/overlay1/res1", "/overlay1/res2" },
        });

        assertFalse(merger1.checkValidUpdate(merger2.getResourceSets()));

        // Test with different source files
        merger1 = createMerger(new String[][] {
                new String[] { "main",    "/main/res1", "/main/res2" },
        });

        // 2nd merger with different order source files in sets.
        merger2 = createMerger(new String[][] {
                new String[] { "main",    "/main/res1" },
        });

        assertFalse(merger1.checkValidUpdate(merger2.getResourceSets()));
    }

    private static ResourceMerger createMerger(String[][] data) {
        ResourceMerger merger = new ResourceMerger();
        for (String[] setData : data) {
            ResourceSet set = new ResourceSet(setData[0]);
            merger.addResourceSet(set);
            for (int i = 1, n = setData.length; i < n; i++) {
                set.addSource(new File(setData[i]));
            }
        }

        return merger;
    }

    private static ResourceMerger getResourceMerger()
            throws DuplicateResourceException, IOException {
        if (sResourceMerger == null) {
            File root = TestUtils.getRoot("baseMerge");

            ResourceSet res = ResourceSetTest.getBaseResourceSet();

            ResourceSet overlay = new ResourceSet("overlay");
            overlay.addSource(new File(root, "overlay"));
            overlay.loadFromFiles();

            sResourceMerger = new ResourceMerger();
            sResourceMerger.addResourceSet(res);
            sResourceMerger.addResourceSet(overlay);
        }

        return sResourceMerger;
    }

    private static File getWrittenResources() throws DuplicateResourceException, IOException {
        ResourceMerger resourceMerger = getResourceMerger();

        File folder = Files.createTempDir();

        resourceMerger.writeResourceFolder(folder);

        return folder;
    }

    /**
     * Returns a folder containing a merger blob data for the given test data folder.
     *
     * This is to work around the fact that the merger blob data contains full path, but we don't
     * know where this project is located on the drive. This rewrites the blob to contain the
     * actual folder.
     * (The blobs written in the test data contains placeholders for the path root and path
     * separators)
     *
     * @param folder
     * @return
     * @throws IOException
     */
    private static File getMergedBlobFolder(File folder) throws IOException {
        File originalMerger = new File(folder, ResourceMerger.FN_MERGER_XML);

        String content = Files.toString(originalMerger, Charsets.UTF_8);

        // search and replace $TOP$ with the root and $SEP$ with the platform separator.
        content = content.replaceAll(
                "\\$TOP\\$", folder.getAbsolutePath()).replaceAll("\\$SEP\\$", File.separator);

        File tempFolder = Files.createTempDir();
        Files.write(content, new File(tempFolder, ResourceMerger.FN_MERGER_XML), Charsets.UTF_8);

        return tempFolder;
    }

    private File getIncMergeRoot(String name) throws IOException {
        File root = TestUtils.getCanonicalRoot("incMergeData");
        return new File(root, name);
    }

    private static File getFolderCopy(File folder) throws IOException {
        File dest = Files.createTempDir();
        copyFolder(folder, dest);
        return dest;
    }

    private static void copyFolder(File from, File to) throws IOException {
        if (from.isFile()) {
            Files.copy(from, to);
        } else if (from.isDirectory()) {
            if (!to.exists()) {
                to.mkdirs();
            }

            File[] children = from.listFiles();
            if (children != null) {
                for (File f : children) {
                    copyFolder(f, new File(to, f.getName()));
                }
            }
        }
    }

    private static void checkImageColor(File file, int expectedColor) throws IOException {
        assertTrue("File '" + file.getAbsolutePath() + "' does not exist.", file.isFile());

        BufferedImage image = ImageIO.read(file);
        int rgb = image.getRGB(0, 0);
        assertEquals(String.format("Expected: 0x%08X, actual: 0x%08X for file %s",
                expectedColor, rgb, file),
                expectedColor, rgb);
    }

    private static Map<String, String> quickStringOnlyValueFileParser(File file)
            throws IOException {
        Map<String, String> result = Maps.newHashMap();

        Document document = ValueResourceParser.parseDocument(file);

        // get the root node
        Node rootNode = document.getDocumentElement();
        if (rootNode == null) {
            return Collections.emptyMap();
        }

        NodeList nodes = rootNode.getChildNodes();

        for (int i = 0, n = nodes.getLength(); i < n; i++) {
            Node node = nodes.item(i);

            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }

            ResourceType type = ValueResourceParser.getType(node);
            if (type != ResourceType.STRING) {
                throw new IllegalArgumentException("Only String resources supported.");
            }
            String name = ValueResourceParser.getName(node);

            String value = null;

            NodeList nodeList = node.getChildNodes();
            nodeLoop: for (int ii = 0, nn = nodes.getLength(); ii < nn; ii++) {
                Node subNode = nodeList.item(ii);

                switch (subNode.getNodeType()) {
                    case Node.COMMENT_NODE:
                        break;
                    case Node.TEXT_NODE:
                        value = subNode.getNodeValue().trim(); // TODO: remove trim.
                        break nodeLoop;
                    case Node.ELEMENT_NODE:
                        break;
                }
            }

            result.put(name, value != null ? value : "");
        }

        return result;
    }
}