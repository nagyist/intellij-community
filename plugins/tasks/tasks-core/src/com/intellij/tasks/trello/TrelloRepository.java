// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.tasks.trello;

import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.tasks.CustomTaskState;
import com.intellij.tasks.Task;
import com.intellij.tasks.TaskBundle;
import com.intellij.tasks.TaskRepositoryType;
import com.intellij.tasks.impl.BaseRepository;
import com.intellij.tasks.impl.httpclient.NewBaseRepositoryImpl;
import com.intellij.tasks.impl.httpclient.TaskResponseUtil;
import com.intellij.tasks.impl.httpclient.TaskResponseUtil.GsonMultipleObjectsDeserializer;
import com.intellij.tasks.impl.httpclient.TaskResponseUtil.GsonSingleObjectDeserializer;
import com.intellij.tasks.trello.model.TrelloBoard;
import com.intellij.tasks.trello.model.TrelloCard;
import com.intellij.tasks.trello.model.TrelloList;
import com.intellij.tasks.trello.model.TrelloUser;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.annotations.Tag;
import org.apache.http.*;
import org.apache.http.client.HttpClient;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestWrapper;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;

/**
 * @author Mikhail Golubev
 */
@Tag("Trello")
public final class TrelloRepository extends NewBaseRepositoryImpl {

  private static final Logger LOG = Logger.getInstance(TrelloRepository.class);
  static final TrelloBoard UNSPECIFIED_BOARD = new TrelloBoard() {
    @Override
    public @NotNull String getName() {
      return "-- from all boards --";
    }
  };
  static final TrelloList UNSPECIFIED_LIST = new TrelloList() {
    @Override
    public @NotNull String getName() {
      return "-- from all lists --";
    }
  };

  // User is actually needed only to check ownership of card (by its id)
  private TrelloUser myCurrentUser;
  private TrelloBoard myCurrentBoard;
  private TrelloList myCurrentList;
  /**
   * Include cards not assigned to current user
   */
  private boolean myIncludeAllCards;

  /**
   * Serialization constructor
   */
  @SuppressWarnings("UnusedDeclaration")
  public TrelloRepository() {
  }

  /**
   * Normal instantiation constructor
   */
  public TrelloRepository(TaskRepositoryType type) {
    super(type);
  }

  /**
   * Cloning constructor
   */
  public TrelloRepository(TrelloRepository other) {
    super(other);
    myCurrentUser = other.myCurrentUser;
    myCurrentBoard = other.myCurrentBoard;
    myCurrentList = other.myCurrentList;
    myIncludeAllCards = other.myIncludeAllCards;
  }

  @Override
  public boolean equals(Object o) {
    if (!super.equals(o)) return false;
    if (o.getClass() != getClass()) return false;
    final TrelloRepository repository = (TrelloRepository)o;
    if (!Comparing.equal(myCurrentUser, repository.myCurrentUser)) return false;
    if (!Comparing.equal(myCurrentBoard, repository.myCurrentBoard)) return false;
    if (!Comparing.equal(myCurrentList, repository.myCurrentList)) return false;
    return myIncludeAllCards == repository.myIncludeAllCards;
  }

  @Override
  public @NotNull BaseRepository clone() {
    return new TrelloRepository(this);
  }

  @Override
  public Task[] getIssues(@Nullable String query, int offset, int limit, boolean withClosed) throws Exception {
    final List<TrelloCard> cards = fetchCards(offset + limit, withClosed);
    return ContainerUtil.map2Array(cards, Task.class, card -> new TrelloTask(card, this));
  }

  @Override
  public @Nullable Task findTask(@NotNull String id) throws Exception {
    final TrelloCard card = fetchCardById(id);
    return card != null ? new TrelloTask(card, this) : null;
  }

  public @Nullable TrelloCard fetchCardById(@NotNull String id) throws Exception {
    try {
      final URIBuilder url = new URIBuilder(getRestApiUrl("cards", id))
        .addParameter("actions", "commentCard")
        .addParameter("fields", TrelloCard.REQUIRED_FIELDS);
      return executeMethod(new HttpGet(url.build()), new GsonSingleObjectDeserializer<>(TrelloUtil.GSON, TrelloCard.class, true));
    }
    // Trello returns string "The requested resource was not found." or "invalid id"
    // if card can't be found, which not only cannot be deserialized, but also not valid JSON at all.
    catch (JsonParseException e) {
      return null;
    }
  }

  public @Nullable TrelloUser getCurrentUser() {
    return myCurrentUser;
  }

  public void setCurrentUser(TrelloUser currentUser) {
    myCurrentUser = currentUser;
  }

  public @Nullable TrelloBoard getCurrentBoard() {
    return myCurrentBoard;
  }

  public void setCurrentBoard(@Nullable TrelloBoard board) {
    myCurrentBoard = board != null && board.getId().equals(UNSPECIFIED_BOARD.getId()) ? UNSPECIFIED_BOARD : board;
  }

  public @Nullable TrelloList getCurrentList() {
    return myCurrentList;
  }

  public void setCurrentList(@Nullable TrelloList list) {
    myCurrentList = list != null && list.getId().equals(UNSPECIFIED_LIST.getId()) ? UNSPECIFIED_LIST : list;
  }

  @Override
  public @Nullable String extractId(@NotNull String taskName) {
    return TrelloUtil.TRELLO_ID_PATTERN.matcher(taskName).matches() ? taskName : null;
  }

  /**
   * Request user information using supplied authorization token
   */
  public @NotNull TrelloUser fetchUserByToken() throws Exception {
    try {
      final URIBuilder url = new URIBuilder(getRestApiUrl("members", "me"))
        .addParameter("fields", TrelloUser.REQUIRED_FIELDS);
      return Objects.requireNonNull(makeRequestAndDeserializeJsonResponse(url.build(), TrelloUser.class));
    }
    catch (Exception e) {
      LOG.warn("Error while fetching initial user info", e);
      // invalidate board and list if user can't be found
      myCurrentBoard = null;
      myCurrentList = null;
      throw e;
    }
  }

  public @NotNull TrelloBoard fetchBoardById(@NotNull String id) throws Exception {
    final URIBuilder url = new URIBuilder(getRestApiUrl("boards", id))
      .addParameter("fields", TrelloBoard.REQUIRED_FIELDS);
    try {
      return Objects.requireNonNull(makeRequestAndDeserializeJsonResponse(url.build(), TrelloBoard.class));
    }
    catch (Exception e) {
      LOG.warn("Error while fetching initial board info", e);
      throw e;
    }
  }

  public @NotNull TrelloList fetchListById(@NotNull String id) throws Exception {
    final URIBuilder url = new URIBuilder(getRestApiUrl("lists", id))
      .addParameter("fields", TrelloList.REQUIRED_FIELDS);
    try {
      return Objects.requireNonNull(makeRequestAndDeserializeJsonResponse(url.build(), TrelloList.class));
    }
    catch (Exception e) {
      LOG.warn("Error while fetching initial list info" + id, e);
      throw e;
    }
  }

  public @NotNull List<TrelloList> fetchBoardLists() throws Exception {
    if (myCurrentBoard == null || myCurrentBoard == UNSPECIFIED_BOARD) {
      throw new IllegalStateException("Board not set");
    }
    return fetchBoardLists(myCurrentBoard.getId());
  }

  private @NotNull List<TrelloList> fetchBoardLists(@NotNull String boardId) throws Exception {
    final URIBuilder url = new URIBuilder(getRestApiUrl("boards", boardId, "lists"))
      .addParameter("fields", TrelloList.REQUIRED_FIELDS);
    return makeRequestAndDeserializeJsonResponse(url.build(), TrelloUtil.LIST_OF_LISTS_TYPE);
  }

  public @NotNull List<TrelloBoard> fetchUserBoards() throws Exception {
    if (myCurrentUser == null) {
      throw new IllegalStateException("User not set");
    }
    final URIBuilder url = new URIBuilder(getRestApiUrl("members", "me", "boards"))
      .addParameter("filter", "open")
      .addParameter("fields", TrelloBoard.REQUIRED_FIELDS);
    return makeRequestAndDeserializeJsonResponse(url.build(), TrelloUtil.LIST_OF_BOARDS_TYPE);
  }

  public @NotNull List<TrelloCard> fetchCards(int limit, boolean withClosed) throws Exception {
    boolean fromList = false;
    // choose most appropriate card provider
    String baseUrl;
    if (myCurrentList != null && myCurrentList != UNSPECIFIED_LIST) {
      baseUrl = getRestApiUrl("lists", myCurrentList.getId(), "cards");
      fromList = true;
    }
    else if (myCurrentBoard != null && myCurrentBoard != UNSPECIFIED_BOARD) {
      baseUrl = getRestApiUrl("boards", myCurrentBoard.getId(), "cards");
    }
    else if (myCurrentUser != null) {
      baseUrl = getRestApiUrl("members", "me", "cards");
    }
    else {
      throw new IllegalStateException("Not configured");
    }
    final URIBuilder fetchCardUrl = new URIBuilder(baseUrl)
      .addParameter("fields", TrelloCard.REQUIRED_FIELDS)
      .addParameter("limit", String.valueOf(limit));
    // 'visible' filter for some reason is not supported for lists
    if (withClosed || fromList) {
      fetchCardUrl.addParameter("filter", "all");
    }
    else {
      fetchCardUrl.addParameter("filter", "visible");
    }
    List<TrelloCard> cards = makeRequestAndDeserializeJsonResponse(fetchCardUrl.build(), TrelloUtil.LIST_OF_CARDS_TYPE);
    LOG.debug("Total " + cards.size() + " cards downloaded");
    if (!myIncludeAllCards) {
      cards = ContainerUtil.filter(cards, card -> card.getIdMembers().contains(myCurrentUser.getId()));
      LOG.debug("Total " + cards.size() + " cards after filtering");
    }
    if (!cards.isEmpty()) {
      if (fromList) {
        baseUrl = getRestApiUrl("boards", cards.get(0).getIdBoard(), "cards");
      }
      // fix for IDEA-111470 and IDEA-111475
      // Select IDs of visible cards, e.d. cards that either archived explicitly, belong to archived list or closed board.
      // This information can't be extracted from single card description, because its 'closed' field
      // reflects only the card state and doesn't show state of parental list and board.
      // NOTE: According to Trello REST API "filter=visible" parameter may be used only when fetching cards for
      // particular board or user.
      final URIBuilder visibleCardsUrl = new URIBuilder(baseUrl)
        .addParameter("filter", "visible")
        .addParameter("fields", "none");
      final List<TrelloCard> visibleCards = makeRequestAndDeserializeJsonResponse(visibleCardsUrl.build(), TrelloUtil.LIST_OF_CARDS_TYPE);
      LOG.debug("Total " + visibleCards.size() + " visible cards");
      final Set<String> visibleCardsIDs = ContainerUtil.map2Set(visibleCards, card -> card.getId());
      for (TrelloCard card : cards) {
        card.setVisible(visibleCardsIDs.contains(card.getId()));
      }
    }
    return cards;
  }

  private @Nullable <T> T executeMethod(@NotNull HttpUriRequest method, @NotNull ResponseHandler<T> handler) throws Exception {
    final HttpClient client = getHttpClient();
    final HttpResponse response = client.execute(method);
    final StatusLine statusLine = response.getStatusLine();
    if (statusLine.getStatusCode() != HttpStatus.SC_OK) {
      final Header header = response.getFirstHeader("Content-Type");
      if (header != null && header.getValue().startsWith("text/plain")) {
        final String entityContent = TaskResponseUtil.getResponseContentAsString(response);
        throw new Exception(TaskBundle.message("failure.server.message", StringUtil.capitalize(entityContent)));
      }
      throw new Exception(TaskBundle.message("failure.http.error", statusLine.getStatusCode(), statusLine.getStatusCode()));
    }
    return handler.handleResponse(response);
  }

  private @NotNull <T> List<T> makeRequestAndDeserializeJsonResponse(@NotNull URI url, @NotNull TypeToken<List<T>> type) throws Exception {
    final List<T> result = executeMethod(new HttpGet(url), new GsonMultipleObjectsDeserializer<>(TrelloUtil.GSON, type));
    return Objects.requireNonNull(result);
  }

  private @Nullable <T> T makeRequestAndDeserializeJsonResponse(@NotNull URI url, @NotNull Class<T> cls) throws Exception {
    return executeMethod(new HttpGet(url), new GsonSingleObjectDeserializer<>(TrelloUtil.GSON, cls));
  }

  @Override
  public String getPresentableName() {
    String pseudoUrl = "trello.com";
    if (myCurrentBoard != null && myCurrentBoard != UNSPECIFIED_BOARD) {
      pseudoUrl += "/" + myCurrentBoard.getName();
    }
    if (myCurrentList != null && myCurrentList != UNSPECIFIED_LIST) {
      pseudoUrl += "/" + myCurrentList.getName();
    }
    return pseudoUrl;
  }

  public boolean isIncludeAllCards() {
    return myIncludeAllCards;
  }

  public void setIncludeAllCards(boolean includeAllCards) {
    myIncludeAllCards = includeAllCards;
  }

  @Override
  public @Nullable CancellableConnection createCancellableConnection() {
    return new HttpTestConnection(new HttpGet(getRestApiUrl("members", "me", "cards") + "?limit=1"));
  }

  /**
   * Add authorization token and developer key in any request to Trello's REST API
   */
  @Override
  protected @Nullable HttpRequestInterceptor createRequestInterceptor() {
    return new HttpRequestInterceptor() {
      @Override
      public void process(HttpRequest request, HttpContext context) throws HttpException, IOException {
        // pass
        if (request instanceof HttpRequestWrapper wrapper) {
          try {
            wrapper.setURI(new URIBuilder(wrapper.getURI())
                             .addParameter("token", myPassword)
                             .addParameter("key", TrelloRepositoryType.DEVELOPER_KEY)
                             .build());
          }
          catch (URISyntaxException e) {
            LOG.error("Illegal URL: " + wrapper.getURI(), e);
          }
        }
        else {
          LOG.error("Cannot add required authentication query parameters to request: " + request);
        }
      }
    };
  }

  @Override
  public boolean isConfigured() {
    return super.isConfigured() && StringUtil.isNotEmpty(getPassword());
  }

  @Override
  public @NotNull String getRestApiPathPrefix() {
    return "/1";
  }

  @Override
  public String getUrl() {
    return "https://api.trello.com";
  }

  @Override
  public @NotNull Set<CustomTaskState> getAvailableTaskStates(@NotNull Task task) throws Exception {
    final TrelloCard card = fetchCardById(task.getId());
    if (card != null) {
      final List<TrelloList> lists = fetchBoardLists(card.getIdBoard());
      final Set<CustomTaskState> result = new HashSet<>();
      for (TrelloList list : lists) {
        if (!list.getId().equals(card.getIdList())) {
          result.add(new CustomTaskState(list.getId(), list.getName()));
        }
      }
      return result;
    }
    return Collections.emptySet();
  }

  @Override
  public void setTaskState(@NotNull Task task, @NotNull CustomTaskState state) throws Exception {
    final URI url = new URIBuilder(getRestApiUrl("cards", task.getId(), "idList")).addParameter("value", state.getId()).build();
    final HttpResponse response = getHttpClient().execute(new HttpPut(url));
    if (response.getStatusLine() != null &&
        response.getStatusLine().getStatusCode() == HttpStatus.SC_UNAUTHORIZED &&
        EntityUtils.toString(response.getEntity()).trim().equals("unauthorized card permission requested")) {
      throw new Exception(TaskBundle.message("trello.failure.write.access.required"));
    }
  }

  @Override
  protected int getFeatures() {
    return super.getFeatures() & ~NATIVE_SEARCH | STATE_UPDATING;
  }
}
