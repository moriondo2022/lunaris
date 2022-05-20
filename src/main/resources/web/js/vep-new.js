const lunarisVariantPredictor = {
    inputFileNames: {},
    statuses: {},
    idsPending: [],
    fieldNames: [],
    stringOperators: ["==", "=~", "!=", "!=~"],
    numericalOperators: ["<", "<=", ">", ">="],
    operators: ["==", "=~", "!=", "!=~", "<", "<=", ">", ">="],
    filterGroupCounter: 0,
    filterCounter: 0,
    filters: [],
    masksList: []
}

const filters = [];
const filterNames = []
const inputFiles = [];
const refGenomes = [];
const outputFormats = [];

const codeMirrorConfig = {
    theme: "liquibyte",
    lineNumbers: true
}

let codeMirror;


function init() {
    initSession();
    getSchema();
    initMasksSelector();
    const codeMirrorParent = document.getElementById("code_mirror_parent");
    codeMirror = CodeMirror(codeMirrorParent, codeMirrorConfig);
    codeMirror.setSize("100%", "7.5em");
    setUpInputDisplays();
}

function setUpInputDisplays(){
    const badgeInputs = document.getElementsByClassName("has-badge");
    let badgeInput;
    for (let i = 0; i < badgeInputs.length; i++) {
        badgeInput = badgeInputs[i];
        badgeInput.onchange = showOnBadge;
    }
    let maskInput = document.getElementById("masks");
    maskInput.onchange = setPredefinedMask;
}

function fourHexDigits(num) {
    return ("000" + num.toString(16)).substr(-4);
}

function isWellFormedSessionId(id) {
    const sessionIdRegex = /^[0-9a-f]{8}$/;
    return id.match(sessionIdRegex);
}

function initSession() {
    let sessionId;
    const queryParts = window.location.search.substring(1).split("&");
    queryParts.forEach ( queryPart => {
        [key, value] = queryPart.split("=");
        if(key === "session" && isWellFormedSessionId(value)) {
            sessionId = value;
        }
    })
    if(sessionId) {
        loadSession(sessionId);
    } else {
        sessionId =
            fourHexDigits((new Date).getTime() % 65536) + fourHexDigits(Math.floor(Math.random() * 65537));
        setSessionId(sessionId);
        //setEmptySubmissionArea();
    }
}

function setSessionId(sessionId) {
    document.getElementById("session_id_area").innerText = sessionId;
    lunarisVariantPredictor.sessionId = sessionId;
}

function loadSession(sessionId) {
    fetch("/lunaris/predictor/session/" + sessionId)
        .then((response) => response.json())
        .then((session) => {
            if(session.error) {
                setSessionMsg("Error:\n" + session.message);
                window.log(session.report);
            } else if(session.found) {
                setSessionId(sessionId);
                if(session.filter) {
                    setMask(session.filter);
                }
                if(session.format) {
                    setOutputFormat(session.format);
                }
                setEmptySubmissionArea();
                session.jobs.forEach(job => {
                    const id = job.id;
                    const path = job.inputFile;
                    const inputFileName = path.substring(path.lastIndexOf("/") + 1);
                    addStatusEntry(inputFileName, id);
                });
                setSessionMsg("Loading session " + sessionId + ".");
            } else {
                const sessionMsg = "Unknown session " + sessionId +
                    ".\nNote that sessions are only saved when something is submitted.";
                setSessionMsg(sessionMsg);
            }
        });
}

function getIdAndLoadSession(){
    const sessionInput = document.getElementById("sessioninput");
    const sessionId = sessionInput.value;
    if (sessionId){
        if(isWellFormedSessionId(sessionId)){
            loadSession(sessionId);
        } else {
            setSessionMsg(sessionId + " is not a valid session ID.");
        }
    }
}

function setSessionMsg(msg) {
    const sessionArea = document.getElementById("session-invalid");
    sessionArea.textContent = msg;
}

function setEmptySubmissionArea() {
    const submissionArea = document.getElementById("submission_area");
    submissionArea.innerHTML =
        "<span id=\"statusUpdatesPlaceholder\">(Submission status updates will appear here)</span>";
}

setInterval(updatePendingStatuses, 300);

function isValidEmail(string) {
    if(!string || string.trim().length === 0) {
        return false;
    }
    const parts = string.split("@");
    if(parts.length !== 2) {
        return false;
    }
    const [user, domain] = parts;
    const domainParts = domain.split("\.");
    return user && user.trim().length > 0 && domainParts.length > 1 &&
        domainParts.every(domainPart => domainPart.trim().length > 0);
}

function showOnBadge(e){
    // is this a good way to do this or not?
    const inputValue = e.target.value;
    const badgeId = e.target.getAttribute("id") + "-badge";
    const badge = document.getElementById(badgeId);
    if (badge){
        badge.textContent = inputValue;   
    }
}

function clearBadges(){
    const badges = document.getElementsByClassName("badge");
    for (let i = 0; i < badges.length; i++){
        console.log(badges[i].textContent);
        badges[i].textContent = "";
    }
}

function setEmailMsg(msg){
    const emailInvalidArea = document.getElementById("email-invalid");
    emailInvalidArea.textContent = msg;
}

function generateEmailMsg(email, isValid){
    let emailMsg = "";
    if (isValid){
        emailMsg = "Submitting job. Notification will be sent to " + email;
    } else {
        emailMsg = email + " is not a valid email. Your job has not been submitted. Please try again.";
    }
    setEmailMsg(emailMsg);
}

function setSaveJobMessage(errorMessage){
    const saveJobMessage = document.getElementById("save-job-message");
    saveJobMessage.innerText = errorMessage;
}

function saveJob(){
    const formData = new FormData();

    const filter = codeMirror.getValue();
    if (filter == ""){
        setSaveJobMessage("Select a filter to proceed.");
        return false;
    }

    const inputFile = getInputFile();
    if (inputFile == ""){
        setSaveJobMessage("Select an input file to proceed.");
        return false;
    }

    const format = getOutputFormat();
    if (format == ""){
        setSaveJobMessage("Select an output format to proceed.");
        return false;
    }

    const hg = getHg();
    if (hg == ""){
        setSaveJobMessage("Select a genome to proceed.");
        return false;
    }

    setSaveJobMessage("");

    filters.push(filter);
    filterNames.push(getMaskSelectNode().value); // TODO evaluate in case this is custom
    inputFiles.push(inputFile);
    outputFormats.push(format);
    refGenomes.push(hg);

    const maskName = getMaskSelectNode().value;
    showNewQueuedJob(maskName, inputFile, format, hg);
    setEmailMsg("");
    return true;
}

function createJobFormData(index, email){
    const jobFormData = new FormData();
    jobFormData.append("filter", filters[index]);
    jobFormData.append("inputFile", inputFiles[index]);
    jobFormData.append("format", outputFormats[index]);
    jobFormData.append("session", lunarisVariantPredictor.sessionId);
    jobFormData.append("hg", refGenomes[index]);
    jobFormData.append("email", email);
    return jobFormData;
}

function saveJobAndCreateNew(){
    if (saveJob()){
        // We don't want to clear the mask someone may have entered if they missed a field and it didn't save.
        clearInputs();
    }
}

function clearInputs(){
    console.log("Clearing inputs");
    resetFilters();
    document.getElementById("inputfile").value = "";
    // First options on these select boxes are placeholders. We'll restore them.
    document.getElementById("hg").value = document.querySelector("#hg option").textContent;
    document.getElementById("formats").value =
        document.querySelector("#formats option").textContent;
    document.getElementById("masks").value =
        document.querySelector("#masks option").textContent;
    clearBadges();
}

function showNewQueuedJob(filter, inputFile, format, hg){
    const newRow = document.createElement("tr");

    const inputFileTd = document.createElement("td");3
    inputFileTd.innerText = trimFilename(inputFile);
    newRow.append(inputFileTd);

    const hgTd = document.createElement("td");
    hgTd.innerText = hg;
    newRow.append(hgTd);

    const maskTd = document.createElement("td");
    maskTd.innerText = filter;
    newRow.append(maskTd);

    const formatTd = document.createElement("td");
    formatTd.innerText = format;
    newRow.append(formatTd);

    const submitTableBody = document.getElementById("submit-table-body");
    submitTableBody.append(newRow);

}

function trimFilename(longFilename){
    let shortFilename = longFilename.split("\\")[-1];
    if (shortFilename){
        return shortFilename;
    }
    return longFilename;
}

function submitAll(){
    const emailInput = document.getElementById("email").value;
    const descriptionInput = document.getElementById("session-desc").value;

    if (inputFiles.length == 0){
        setEmailMsg("There are no jobs queued for submission.");
        return;
    }

    if (descriptionInput == ""){
        setEmailMsg("Enter a description for this session in order to continue.");
        return;
    }
    // As of now, must have email in order to submit.
    if (emailInput == ""){
        setEmailMsg("Enter your email to continue.");
        return;
    }
    if(isValidEmail(emailInput)) {
        lunarisVariantPredictor.email = emailInput;
        generateEmailMsg(emailInput, true);
    } else {
        generateEmailMsg(emailInput, false);
        return;
    }

    for (let i = 0; i < inputFiles.length; i++){
        const formData = createJobFormData(i, emailInput);
        const inputFile = inputFiles[i];
        console.log("Submitting " + formData);
        fetch("/lunaris/predictor/upload", {method: "POST", body: formData})
            .then((response) => {
                if (!response.ok) {
                    throw "Could not submit " + inputFile.name + ": " + response.statusText;
                }
                return response.text();
            })
            .then((id) => {
                // TODO figure out everything to do with id. does this mean session id?
                addStatusEntry(inputFile.name, id, i);
                getStatus(id);
            }).catch(showCouldNotSubmit);
    }
}

function getSchema() {
    fetch("/lunaris/predictor/schema")
        .then((response) => {
            return response.json();
        })
        .then((schema) => {
            if (schema.isError) {
                const statusTextNode = getStatusAreaNode();
                statusTextNode.innerText = "Unable to load available fields: " + schema.message;
            }
            if (schema.col_names) {
                lunarisVariantPredictor.fieldNames = schema.col_names;
                const fieldsSelectNode = document.getElementById("fields");
                setOptionsForSelect(fieldsSelectNode, schema.col_names);
            }
        })
}

function getStatusAreaNode() {
    return document.getElementById("status_area");
}

function getSubmissionAreaNode() {
    return document.getElementById("submission_area");
}

function showCouldNotSubmit(message) {
    /*const pNode = document.createElement("p");
    pNode.innerText = message;
    const statusAreaNode = getSubmissionAreaNode();
    statusAreaNode.append(pNode);*/
    console.log(message);
}

function addStatusEntry(inputFileName, id, jobIndex) {
    lunarisVariantPredictor.inputFileNames[id] = inputFileName;
    lunarisVariantPredictor.idsPending.push(id);
    const statusRow = document.createElement("tr");
    const statusAreaNode = getSubmissionAreaNode();
    const placeholder = document.getElementById("statusUpdatesPlaceholder");
    if(placeholder) {
        placeholder.innerText = "";
    }
    statusAreaNode.appendChild(statusRow);
    statusRow.setAttribute("id", id);
    showInitialStatus(statusRow, inputFileName, jobIndex);
    console.log("Very first time adding status for: " + inputFileName);
}

function showInitialStatus(statusRow, inputFileName, jobIndex) {
    // Include the file name, reference genome, mask filter, output format, and restore link
    const inputFileCell = document.createElement("td");
    inputFileCell.innerText = inputFileName;
    statusRow.appendChild(inputFileCell);

    const refGenomeCell = document.createElement("td");
    refGenomeCell.innerText = refGenomes[jobIndex];
    statusRow.appendChild(refGenomeCell);

    const filterNameCell = document.createElement("td");
    filterNameCell.innerText = filterNames[jobIndex];
    statusRow.appendChild(filterNameCell);

    const outputFormatCell = document.createElement("td");
    outputFormatCell.innerText = outputFormats[jobIndex];
    statusRow.appendChild(outputFormatCell);

    const outputFileCell = document.createElement("td");
    outputFileCell.innerText = "Processing...";
    outputFileCell.setAttribute("class", "output-file");
    statusRow.appendChild(outputFileCell);

    const restoreJobCell = document.createElement("td");
    restoreJobCell.innerHTML = "<a>Restore</a>";
    statusRow.appendChild(restoreJobCell);
}

function getStatus(id) {
    fetch("/lunaris/predictor/status/" + id)
        .then((response) => response.json())
        .then((status) => {
            lunarisVariantPredictor.statuses[id] = status;
            showStatus(id);
        });
}

function soManyErrors(nSnags) {
    if(nSnags === 0) {
        return "No errors";
    } else if(nSnags === 1) {
        return "One error";
    } else {
        return `${nSnags} errors`;
    }
}

function showStatus(id) {
    const statusRow = document.getElementById(id);
    const outputFileCell = statusRow.getElementsByClassName("output-file")[0];
    const inputFileName = lunarisVariantPredictor.inputFileNames[id];
    const status = lunarisVariantPredictor.statuses[id];
    if (status) {
        if (status.succeeded) {
            const linkNode = document.createElement("a");
            const outputFile = id + ".tsv"
            linkNode.setAttribute("href", "/lunaris/predictor/results/" + outputFile);
            linkNode.setAttribute("download", outputFile);
            linkNode.innerText = "Click here to download";
            outputFileCell.append(linkNode);
        }
        const snagMessages = status.snagMessages;
        const nSnags = snagMessages.length;
        /*if(nSnags) {
            const snagNode = document.createTextNode(" " + soManyErrors(nSnags));
            const snagNodeSpan = document.createElement("span");
            snagNodeSpan.style.color = "red";
            snagNodeSpan.appendChild(snagNode)
            outputFileCell.append(snagNodeSpan);
            const snagMessagesClass = "snagMessages";
            if(!statusRow.getElementsByClassName(snagMessagesClass).length) {
                const snagsDivNode = document.createElement("div");
                snagsDivNode.innerText = snagMessages.join("\n");
                snagsDivNode.classList.add(snagMessagesClass);
                snagsDivNode.style.height = "5em";
                snagsDivNode.style.width = "95%"
                snagsDivNode.style.margin = "auto";
                snagsDivNode.style.overflowY = "scroll";
                snagsDivNode.style.color = "red";
                statusRow.appendChild(snagsDivNode);
            }
        }*/
    } else {
        showInitialStatus(statusRow, inputFileName);
    }
}

function updatePendingStatuses() {
    const idsPendingNew = [];
    let i = 0;
    const idsPending = lunarisVariantPredictor.idsPending;
    while (i < idsPending.length) {
        const id = idsPending[i];
        getStatus(id);
        showStatus(id);
        const status = lunarisVariantPredictor.statuses[id];
        if (!status.completed) {
            idsPendingNew.push(id);
        }
        i++;
    }
    lunarisVariantPredictor.idsPending = idsPendingNew;
}

function setOptionsForSelect(selectNode, options) {
    options.forEach((option) => {
        selectNode.options.add(new Option(option));
    })
}

function resetFilters() {
    codeMirror.setValue("");
}

function initMasksSelector() {
    fetch("/lunaris/predictor/masks/list")
        .then((response) => response.json())
        .then((masksList) => {
            lunarisVariantPredictor.masksList = masksList;
            const selectNode = getMaskSelectNode();
            setOptionsForSelect(selectNode, lunarisVariantPredictor.masksList);
        });
}

function getMaskSelectNode() {
    return document.getElementById("masks");
}

function getOutputFormatNode() {
    return document.getElementById("formats");
}

function setOutputFormat(format) {
    getOutputFormatNode().value = format;
}

function getOutputFormat() {
    const selection = getOutputFormatNode().value;
    if (selection == "-- Choose format --"){
        return "";
    }
    return selection;
}

function getInputFile(){
    return document.getElementById("inputfile").value;
}

function getHg() {
    const selection = document.getElementById("hg").value;
    if (selection == "-- Choose genome --"){
        return "";
    }
    return selection;
}

function setMask(mask) {
    if (mask.slice(0, 5) == "ERROR"){
        mask = "";
    }
    codeMirror.setValue(mask);
}

function setPredefinedMask(e) {
    const maskSelectNode = getMaskSelectNode();
    const maskName = maskSelectNode.value;
    fetch("/lunaris/predictor/masks/" + maskName)
        .then((response) => response.text())
        .then((mask) => {
            setMask(mask);
        });
}

