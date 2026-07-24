import { createBrowserRouter, createRoutesFromElements, Route } from "react-router";
import App from "../App";
import Login from "../pages/Login";
import Register from "../pages/Register";
import Home from "../pages/Home";
import { GuestOnlyRoute, RequireAuth } from './guards';

const routeElements = createRoutesFromElements(
    <Route path={'/'} element={<App />}>
        <Route element={<GuestOnlyRoute />}>
            <Route path={'login'} element={<Login />} />
            <Route path={'register'} element={<Register />} />
        </Route>
        <Route element={<RequireAuth />}>
            <Route path={'home'} element={<Home />} />
        </Route>
    </Route>
);

const router = createBrowserRouter(routeElements);

export default router;
