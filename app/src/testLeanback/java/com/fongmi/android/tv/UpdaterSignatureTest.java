package com.fongmi.android.tv;

import android.app.Application;
import android.content.pm.PackageInfo;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowSigningInfo;
import org.robolectric.util.ReflectionHelpers;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.robolectric.shadow.api.Shadow.extract;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
public class UpdaterSignatureTest {

    private static final Signature ORIGINAL = new Signature("01");
    private static final Signature ROTATED = new Signature("02");
    private static final Signature OTHER = new Signature("03");

    @Test
    public void legacyOnlyPackagesCanMatchWithoutChecksum() {
        assertTrue(Updater.signaturesMatch(legacy(ORIGINAL), legacy(ORIGINAL), false));
        assertFalse(Updater.signaturesMatch(legacy(ORIGINAL), legacy(OTHER), true));
    }

    @Test
    public void eitherPackageCanFallBackWhenModernSignersAreMissing() {
        PackageInfo emptyModern = modern();
        emptyModern.signatures = new Signature[]{ORIGINAL};
        assertTrue(Updater.signaturesMatch(emptyModern, modern(ORIGINAL), false));
        assertTrue(Updater.signaturesMatch(modern(ORIGINAL), emptyModern, false));
    }

    @Test
    public void unreadableInstalledSignatureIsRejectedEvenWithChecksum() {
        assertFalse(Updater.signaturesMatch(new PackageInfo(), legacy(ORIGINAL), true));
    }

    @Test
    public void unreadableArchiveRequiresVerifiedChecksum() {
        assertFalse(Updater.signaturesMatch(legacy(ORIGINAL), new PackageInfo(), false));
        assertTrue(Updater.signaturesMatch(legacy(ORIGINAL), new PackageInfo(), true));
    }

    @Test
    public void modernMismatchCannotFallBackToMatchingLegacyFields() {
        PackageInfo installed = modern(ORIGINAL);
        PackageInfo archive = modern(OTHER);
        installed.signatures = archive.signatures = new Signature[]{ORIGINAL};
        assertFalse(Updater.signaturesMatch(installed, archive, true));
    }

    @Test
    public void singleSignerRotationUsesTheCandidateLineage() {
        PackageInfo installed = modern(ORIGINAL);
        PackageInfo rotated = modern(ROTATED);
        ShadowSigningInfo signing = extract(rotated.signingInfo);
        signing.setPastSigningCertificates(new Signature[]{ORIGINAL, ROTATED});
        rotated.signatures = new Signature[]{ORIGINAL};
        assertTrue(Updater.signaturesMatch(installed, rotated, false));
        assertFalse(Updater.signaturesMatch(rotated, installed, true));
        assertFalse(Updater.signaturesMatch(legacy(ORIGINAL), rotated, true));
    }

    @Test
    public void multipleSignersMustMatchAsACompleteSet() {
        assertTrue(Updater.signaturesMatch(modern(ORIGINAL, OTHER), legacy(OTHER, ORIGINAL), false));
        assertFalse(Updater.signaturesMatch(modern(ORIGINAL, OTHER), legacy(ORIGINAL), true));
    }

    private static PackageInfo legacy(Signature... signers) {
        PackageInfo info = new PackageInfo();
        info.signatures = signers;
        return info;
    }

    private static PackageInfo modern(Signature... signers) {
        PackageInfo info = new PackageInfo();
        info.signingInfo = ReflectionHelpers.callConstructor(SigningInfo.class);
        ShadowSigningInfo signing = extract(info.signingInfo);
        signing.setSignatures(signers);
        return info;
    }
}
