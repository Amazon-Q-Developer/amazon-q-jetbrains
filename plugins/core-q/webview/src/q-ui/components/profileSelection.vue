<!-- SPDX-License-Identifier: Apache-2.0 -->

<template>
    <div @keydown.enter="handleContinueClick">
        <div class="font-amazon">
            <template v-if="isWaitingResponse">
                <div class="title bottom-small-gap">Fetching Q Developer profiles...this may take a minute.</div>
            </template>

            <template v-else-if="isNotAcceptingNewCustomers">
                <div id="not-accepting-new-customers" class="blocked-screen">
                    <div class="blocked-heading">New sign-ups are no longer available</div>
                    <div class="blocked-subheading">
                        Amazon Q Developer stopped accepting new accounts as of {{ signupCutoffDate }}.
                    </div>

                    <section class="blocked-card blocked-card-info">
                        <h3 class="blocked-card-title">
                            <svg class="blocked-icon" viewBox="0 0 16 16" aria-hidden="true">
                                <circle cx="8" cy="8" r="7" fill="none" stroke="currentColor" stroke-width="1.5"/>
                                <rect x="7.25" y="6.75" width="1.5" height="5" rx="0.6" fill="currentColor"/>
                                <circle cx="8" cy="4.6" r="1" fill="currentColor"/>
                            </svg>
                            Why am I seeing this?
                        </h3>
                        <p class="blocked-card-body">
                            Amazon Q Developer IDE plugins are reaching end of support on {{ endOfSupportDate }}.
                            New Builder ID accounts created after {{ signupCutoffDate }} can no longer access
                            Q Developer.
                            <a class="blocked-inline-link" :href="announcementUrl"
                               @click.prevent="openExternalUrl(announcementUrl)">Read the announcement &rarr;</a>
                        </p>
                    </section>

                    <section class="blocked-card blocked-card-highlight">
                        <h3 class="blocked-card-title blocked-card-title-highlight">
                            <svg class="blocked-icon" viewBox="0 0 16 16" aria-hidden="true">
                                <path d="M8 1.2c1.9 1.7 3 4.1 3 6.4L9.8 8.9H6.2L5 7.6c0-2.3 1.1-4.7 3-6.4zm-3 8.1L3.3 12.7l2.4-1.1-.7-2.3zm6 0 1.7 3.4-2.4-1.1.7-2.3zM7 10.2h2l-1 3.6-1-3.6z" fill="currentColor"/>
                            </svg>
                            What should I use instead?
                        </h3>
                        <p class="blocked-card-body">
                            We've built
                            <span class="kiro-badge">
                                <svg class="blocked-icon-sm" viewBox="0 0 16 16" aria-hidden="true">
                                    <path d="M9 1L3 9h4l-1 6 6-8H8l1-6z" fill="currentColor"/>
                                </svg>
                                Kiro
                            </span>
                            &mdash; an agentic IDE with spec-driven development, hooks, steering files, and all the
                            AI coding features you loved in Q Developer.
                        </p>
                        <p class="blocked-card-body">
                            Kiro includes all the AI coding features from Q Developer, plus spec-driven development
                            and more. Get started free at
                            <a class="blocked-inline-link" :href="kiroUrl"
                               @click.prevent="openExternalUrl(kiroUrl)">kiro.dev</a>.
                        </p>
                    </section>

                    <section class="blocked-card blocked-card-tip">
                        <h3 class="blocked-card-title blocked-card-title-tip">
                            <svg class="blocked-icon" viewBox="0 0 16 16" aria-hidden="true">
                                <path d="M8 1.5a4.5 4.5 0 0 0-2.6 8.2v1.1c0 .4.3.7.7.7h3.8c.4 0 .7-.3.7-.7V9.7A4.5 4.5 0 0 0 8 1.5z" fill="currentColor"/>
                                <rect x="6.1" y="12.6" width="3.8" height="1.3" rx="0.6" fill="currentColor"/>
                            </svg>
                            Already have an account?
                        </h3>
                        <p class="blocked-card-body">
                            If your Builder ID was created <strong>before {{ signupCutoffDate }}</strong>, you can
                            still sign in. Try logging in with your existing credentials &mdash; only newly created
                            accounts are blocked.
                        </p>
                    </section>

                    <button id="get-started-with-kiro" class="blocked-btn-primary font-amazon"
                            @click="openExternalUrl(kiroUrl)">
                        &rarr;&nbsp; Get started with Kiro
                    </button>
                    <button id="go-back" class="blocked-btn-secondary font-amazon" @click="handleGoBackClick()">
                        Try a different login method
                    </button>
                    <a id="read-announcement" class="blocked-footer-link" :href="announcementUrl"
                       @click.prevent="openExternalUrl(announcementUrl)">Read the full announcement</a>
                </div>
            </template>

            <template v-else>
                <!-- Title & Subtitle -->
                <div id="profile-page" class="profile-header">
                    <h2 class="title bottom-small-gap">Choose a Q Developer profile</h2>
                    <div class="profile-subtitle">
                        Your administrator has given you access to Q from multiple profiles.
                        Choose the profile that meets your current working needs. You can change your profile at any time.
                        <a @click.prevent="openUrl">More info.</a>
                    </div>
                </div>
                <!-- Profile List -->
                <div class="profile-list">
                    <div
                        v-for="(profile, index) in availableProfiles"
                        :key="index"
                        class="profile-item bottom-small-gap"
                        :class="{ selected: selectedProfile?.arn === profile.arn }"
                        @click="toggleItemSelection(profile)"
                        tabindex="0"
                    >
                        <div class="text">
                            <div class="profile-name">{{ profile.profileName }} - <span class="profile-region">{{ profile.region }}</span></div>
                            <div class="profile-id">Account: {{ profile.accountId }}</div>
                        </div>
                    </div>
                </div>

                <div v-if="errorMessage" style="color: white; margin-bottom: 10px;">
                    {{ errorMessage }}
                </div>
                <div v-if="errorMessage" class="button-row">
                    <button
                        class="login-flow-button continue-button font-amazon"
                        :disabled="isRefreshing"
                        @click="handleRetryClick"
                    >
                        {{ isRefreshing ? 'Refreshing...' : 'Try Again' }}
                    </button>
                    <button
                        class="login-flow-button continue-button font-amazon"
                        @click="handleSignoutClick()"
                    >
                        Sign Out
                    </button>
                </div>
                <!-- Continue Button -->
                <div v-else>
                    <button
                        class="login-flow-button continue-button font-amazon"
                        :disabled="selectedProfile === null"
                        v-on:click="handleContinueClick()"
                        tabindex="-1"
                    >
                        Continue
                    </button>
                </div>
            </template>
        </div>
    </div>
</template>

<script lang="ts">
import { defineComponent } from 'vue'
import { Profile, GENERIC_PROFILE_LOAD_ERROR, ListProfilePendingResult, ListProfileSuccessResult, ListProfileFailureResult } from '../../model'

export default defineComponent({
    name: 'ProfileSelection',
    props: {
        app: { type: String, default: '' }
    },
    data() {
        return {
            selectedProfile: undefined as Profile | undefined,
            availableProfiles: [] as Profile[],
            errorMessage: undefined as string | undefined,
            isRefreshing: false as boolean,
            isNotAcceptingNewCustomers: false as boolean,
            // Product copy, not derived from the service response. The service message is still stored
            // (it is what marks the identity as blocked) but is no longer displayed: this screen
            // explains the situation and the way forward, which the raw message does not.
            signupCutoffDate: 'May 15, 2026',
            endOfSupportDate: 'April 30, 2027',
            kiroUrl: 'https://kiro.dev',
            announcementUrl: 'https://aws.amazon.com/blogs/devops/amazon-q-developer-end-of-support-announcement/',
        }
    },
    computed: {
        isWaitingResponse() {
            this.errorMessage = ''
            this.isNotAcceptingNewCustomers = false
            const profileResult = this.$store.state.listProfilesResult
            if (profileResult instanceof ListProfilePendingResult) {
                return true
            }

            if (profileResult instanceof ListProfileSuccessResult) {
                this.availableProfiles = profileResult.profiles
            } else if (profileResult instanceof ListProfileFailureResult) {
                if (profileResult.notAcceptingNewCustomers) {
                    // Permanent rejection: show the real service message, which explains why, and
                    // offer only "Go back". The generic message and Try Again would both mislead.
                    this.isNotAcceptingNewCustomers = true
                    this.errorMessage = profileResult.errorMessage || GENERIC_PROFILE_LOAD_ERROR
                } else {
                    this.errorMessage = GENERIC_PROFILE_LOAD_ERROR
                }
                this.isRefreshing = false
            } else {
                // should not be this path
                this.errorMessage = "Unexpected error happenede while loading Q Webview page"
            }
        }
    },
    mounted() {
        window.ideApi.postMessage({command: 'listProfiles'})
    },

    methods: {
        toggleItemSelection(profile: Profile) {
            this.selectedProfile = profile;
        },
        handleContinueClick() {
            if (this.selectedProfile) {
                this.$store.commit('setSelectedProfile', this.selectedProfile);
                const switchProfileMessage = {
                    command: 'switchProfile',
                    profileName: this.selectedProfile.profileName,
                    accountId: this.selectedProfile.accountId,
                    region: this.selectedProfile.region,
                    arn: this.selectedProfile.arn
                };
                window.ideApi.postMessage(switchProfileMessage);
            }
        },
        handleRetryClick() {
            this.isRefreshing = true
            window.ideApi.postMessage({command: 'listProfiles'})
        },
        handleSignoutClick() {
            window.ideApi.postMessage({command: 'signout'})
        },
        /**
         * Amazon Q Developer is no longer accepting this customer, so there is nothing to retry.
         * Sign out to return the user to a neutral login screen. The IDE-side handler for this
         * command already no-ops when no connection is present, so this is safe even if the
         * connection was cleared while the error was being shown.
         */
        handleGoBackClick() {
            window.ideApi.postMessage({command: 'signout'})
        },
        openExternalUrl(externalLink: string) {
            // Links must go through the IDE rather than the embedded browser, same as openUrl below.
            // The anchor also carries a real href purely for affordance: browsers only apply
            // cursor: pointer to anchors that have one, and without it the link rendered as blue text
            // that gave no sign it could be clicked -- reported in the bug bash as "not clickable".
            // @click.prevent stops the webview itself from navigating.
            window.ideApi.postMessage({
                command: 'openUrl',
                externalLink
            })
        },
        openUrl() {
            window.ideApi.postMessage({
                command: 'openUrl',
                externalLink: 'https://docs.aws.amazon.com/amazonq/latest/qdeveloper-ug/subscribe-understanding-profile.html'
            })
        }
    }
})
</script>
<style scoped lang="scss">
/* --- Access-blocked screen ---------------------------------------------------------------------
   Colours follow the JetBrains webview's existing palette where possible; only the accent hues and
   the primary button gradient are fixed, since those carry meaning rather than chrome.
   -------------------------------------------------------------------------------------------- */
.blocked-screen {
    text-align: left;
    padding: 0 4px;

    /* Every login stage sits inside .auth-container.centered-with-max-width, which caps width at
       260px. That is too narrow for these cards, and the cap cannot be widened here because it is on
       an ancestor owned by login.vue and shared with the other stages. Centring on the parent's axis
       and letting the content exceed the cap symmetrically keeps this change inside the component
       that owns the screen. max-width stays relative to the viewport so it never overflows a narrow
       tool window. */
    width: 90vw;
    max-width: 380px;
    margin-left: 50%;
    transform: translateX(-50%);

    .blocked-heading {
        font-size: 15px;
        font-weight: 700;
        text-align: center;
        color: white;
    }

    .blocked-subheading {
        margin-top: 4px;
        margin-bottom: 14px;
        text-align: center;
        font-size: 13px;
        opacity: 0.75;
    }

    .blocked-card {
        border: 1px solid rgba(255, 255, 255, 0.18);
        border-radius: 8px;
        padding: 10px 12px;
        margin-bottom: 10px;

        &.blocked-card-highlight {
            border-color: #4a6cf7;
        }

        &.blocked-card-tip {
            border-color: #3fb950;
        }
    }

    .blocked-card-title {
        display: flex;
        align-items: center;
        gap: 6px;
        margin: 0 0 6px 0;
        font-size: 12px;
        font-weight: 700;
        letter-spacing: 0.04em;
        text-transform: uppercase;
        color: #f2b100;

        &.blocked-card-title-highlight {
            color: #4a6cf7;
        }

        &.blocked-card-title-tip {
            color: #3fb950;
        }
    }

    .blocked-icon {
        width: 13px;
        height: 13px;
        flex: 0 0 auto;
    }

    .blocked-icon-sm {
        width: 10px;
        height: 10px;
    }

    .blocked-card-body {
        margin: 0 0 6px 0;
        font-size: 13px;
        line-height: 1.45;
        color: white;

        &:last-child {
            margin-bottom: 0;
        }
    }

    .kiro-badge {
        display: inline-flex;
        align-items: center;
        gap: 3px;
        padding: 1px 6px;
        border-radius: 10px;
        font-weight: 600;
        color: #4a6cf7;
        background: rgba(74, 108, 247, 0.14);
    }

    /* No underline until hover, matching the mock. These carry an href purely so the pointer cursor
       appears -- browsers only apply it to anchors that have one -- while @click.prevent stops the
       webview navigating and routes the URL through the IDE. */
    .blocked-inline-link,
    .blocked-footer-link {
        text-decoration: none;
        cursor: pointer;

        &:hover {
            text-decoration: underline;
        }
    }

    .blocked-btn-primary,
    .blocked-btn-secondary {
        display: block;
        width: 100%;
        box-sizing: border-box;
        padding: 8px 10px;
        margin-top: 8px;
        border-radius: 6px;
        font-size: 13px;
        text-align: center;
        cursor: pointer;
    }

    .blocked-btn-primary {
        background: linear-gradient(90deg, #4a6cf7 0%, #7a5af8 100%);
        border: none;
        color: #ffffff;
        font-weight: 700;
    }

    .blocked-btn-secondary {
        background: transparent;
        border: 1px solid rgba(255, 255, 255, 0.28);
        color: white;
    }

    .blocked-footer-link {
        display: block;
        margin-top: 12px;
        text-align: center;
        font-size: 13px;
    }
}

.profile-header {
    margin-bottom: 16px;
}

.profile-subtitle {
    font-size: 12px;
    color: #bbbbbb;
    margin-bottom: 12px;
}

.profile-list {
    display: flex;
    flex-direction: column;
}

.profile-item {
    padding: 15px;
    display: flex;
    align-items: flex-start;
    border: 1px solid #cccccc;
    border-radius: 4px;
    margin-bottom: 10px;
    cursor: pointer;
    transition: background 0.2s ease-in-out;
}

.button-row :deep(.login-flow-button) {
    margin-bottom: 10px;
}

.button-row {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 10px;
    margin-top: 20px;
}

.selected {
    user-select: none;
}

.text {
    display: flex;
    flex-direction: column;
    font-size: 15px;
}

.profile-name {
    font-weight: bold;
    margin-bottom: 2px;
}

.profile-region {
    font-style: italic;
    color: #bbbbbb;
}

.profile-description {
    font-size: 12px;
    color: #bbbbbb;
}

body.jb-dark {
    .profile-item {
        border: 1px solid white;
    }

    .selected {
        border: 1px solid #29a7ff;
    }
}

body.jb-light {
    .profile-item {
        border: 1px solid black;
    }

    .selected {
        border: 1px solid #3574f0;
    }
}
</style>
