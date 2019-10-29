export const getCSRFToken = ():string => {
    const elem : HTMLInputElement = document.getElementById("__anti-forgery-token") as unknown as HTMLInputElement
    const token : string = elem.value as unknown as string;
    return token
}